use log::{debug, error, info};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use yrs::updates::decoder::Decode;
use yrs::{Any, Array, ArrayRef, Doc, Out, ReadTxn, StateVector, Transact, Update};

uniffi::setup_scaffolding!();

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct TaskEntry {
    pub uuid: String,
    pub content: String,
    pub is_pinned: bool,
    pub created_at: i64,
    pub updated_at: i64,
    pub tags: Vec<String>,
}

#[derive(uniffi::Object)]
pub struct TaskDocument {
    doc: Doc,
    tasks_array: ArrayRef,
}

#[uniffi::export]
impl TaskDocument {
    #[uniffi::constructor]
    pub fn new() -> Self {
        #[cfg(target_os = "android")]
        {
            android_logger::init_once(
                android_logger::Config::default()
                    .with_tag("RustSync")
                    .with_max_level(log::LevelFilter::Debug),
            );
        }
        info!("TaskDocument initialized");

        let doc = Doc::new();
        let tasks_array = doc.get_or_insert_array("tasks");
        Self { doc, tasks_array }
    }

    pub fn get_all_tasks_json(&self) -> String {
        let txn = self.doc.transact();
        let mut tasks = Vec::new();

        for value in self.tasks_array.iter(&txn) {
            // Support both YMap (if we ever figure it out) and Any::Map
            if let Out::Any(Any::Map(map)) = value {
                let uuid = map
                    .get("uuid")
                    .and_then(|v| {
                        if let Any::String(s) = v {
                            Some(s.to_string())
                        } else {
                            None
                        }
                    })
                    .unwrap_or_default();
                let content = map
                    .get("content")
                    .and_then(|v| {
                        if let Any::String(s) = v {
                            Some(s.to_string())
                        } else {
                            None
                        }
                    })
                    .unwrap_or_default();

                let is_pinned = map
                    .get("is_pinned")
                    .and_then(|v| if let Any::Bool(b) = v { Some(*b) } else { None })
                    .unwrap_or(false);

                let created_at = Self::get_i64_from_any(map.get("created_at"));
                let updated_at = Self::get_i64_from_any(map.get("updated_at"));

                let mut tags_vec = Vec::new();
                if let Some(Any::Array(tags_array)) = map.get("tags") {
                    for tag_val in tags_array.iter() {
                        if let Any::String(s) = tag_val {
                            tags_vec.push(s.to_string());
                        }
                    }
                }

                tasks.push(TaskEntry {
                    uuid,
                    content,
                    is_pinned,
                    created_at,
                    updated_at,
                    tags: tags_vec,
                });
            }
        }

        debug!("get_all_tasks_json returning {} tasks", tasks.len());
        serde_json::to_string(&tasks).unwrap_or_else(|_| "[]".to_string())
    }

    pub fn restore_from_json(&self, json: String) {
        let tasks: Vec<TaskEntry> = serde_json::from_str(&json).unwrap_or_default();
        let task_count = tasks.len();
        let mut txn = self.doc.transact_mut();

        let len = self.tasks_array.len(&txn);
        if len > 0 {
            self.tasks_array.remove_range(&mut txn, 0, len);
        }

        for task in tasks {
            Self::append_task_internal(&mut txn, &self.tasks_array, task);
        }
        info!("restored {} tasks from JSON", task_count);
    }

    pub fn add_task(&self, json: String) {
        match serde_json::from_str::<TaskEntry>(&json) {
            Ok(task) => {
                let uuid = task.uuid.clone();
                let mut txn = self.doc.transact_mut();
                Self::insert_task_internal(&mut txn, &self.tasks_array, 0, task);
                debug!("add_task success for {}", uuid);
            }
            Err(e) => {
                error!("add_task failed to parse JSON: {}", e);
            }
        }
    }

    pub fn update_task(&self, uuid: String, json: String) {
        if let Ok(new_task) = serde_json::from_str::<TaskEntry>(&json) {
            let mut txn = self.doc.transact_mut();
            if let Some((index, _)) = self.find_task_index_by_uuid(&txn, &uuid) {
                self.tasks_array.remove(&mut txn, index);
                let any_task = Self::task_to_any(new_task);
                self.tasks_array.insert(&mut txn, index, any_task);
                debug!("update_task success for {}", uuid);
            } else {
                debug!("update_task failed - UUID not found: {}", uuid);
            }
        }
    }

    pub fn delete_task(&self, uuid: String) {
        let mut txn = self.doc.transact_mut();
        if let Some((index, _)) = self.find_task_index_by_uuid(&txn, &uuid) {
            self.tasks_array.remove(&mut txn, index);
            debug!("delete_task success for {}", uuid);
        } else {
            debug!("delete_task failed - UUID not found: {}", uuid);
        }
    }

    pub fn get_update(&self) -> Vec<u8> {
        let txn = self.doc.transact();
        txn.encode_state_as_update_v1(&StateVector::default())
    }

    pub fn apply_update(&self, update: Vec<u8>) {
        let mut txn = self.doc.transact_mut();
        if let Ok(update) = Update::decode_v1(&update) {
            let _ = txn.apply_update(update);
            info!("applied update");
        }
    }
}

impl TaskDocument {
    fn get_i64_from_any(val: Option<&Any>) -> i64 {
        val.and_then(|v| match v {
            Any::BigInt(n) => Some(*n),
            Any::Number(n) => Some(*n as i64),
            _ => None,
        })
        .unwrap_or(0)
    }

    fn task_to_any(task: TaskEntry) -> Any {
        let mut map = HashMap::new();
        map.insert("uuid".to_string(), Any::String(task.uuid.into()));
        map.insert("content".to_string(), Any::String(task.content.into()));
        map.insert("is_pinned".to_string(), Any::Bool(task.is_pinned));
        map.insert("created_at".to_string(), Any::BigInt(task.created_at));
        map.insert("updated_at".to_string(), Any::BigInt(task.updated_at));

        let mut tags_vec = Vec::new();
        for tag in task.tags {
            tags_vec.push(Any::String(tag.into()));
        }
        map.insert(
            "tags".to_string(),
            Any::Array(Arc::from(tags_vec.into_boxed_slice())),
        );

        Any::Map(Arc::new(map))
    }

    fn append_task_internal(txn: &mut yrs::TransactionMut, array: &ArrayRef, task: TaskEntry) {
        let any_task = Self::task_to_any(task);
        array.push_back(txn, any_task);
    }

    fn insert_task_internal(
        txn: &mut yrs::TransactionMut,
        array: &ArrayRef,
        index: u32,
        task: TaskEntry,
    ) {
        let any_task = Self::task_to_any(task);
        array.insert(txn, index, any_task);
    }

    fn find_task_index_by_uuid<'a>(
        &self,
        txn: &'a yrs::TransactionMut,
        target_uuid: &str,
    ) -> Option<(u32, HashMap<String, Any>)> {
        let mut index = 0;
        for value in self.tasks_array.iter(txn) {
            if let Out::Any(Any::Map(map)) = value {
                if let Some(Any::String(uuid)) = map.get("uuid") {
                    if &**uuid == target_uuid {
                        return Some((index, (*map).clone()));
                    }
                }
            }
            index += 1;
        }
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_task_crud() {
        let doc = TaskDocument::new();

        let task1 = TaskEntry {
            uuid: "u1".to_string(),
            content: "Task 1".to_string(),
            is_pinned: false,
            created_at: 100,
            updated_at: 100,
            tags: vec!["a".to_string()],
        };
        doc.add_task(serde_json::to_string(&task1).unwrap());

        let json = doc.get_all_tasks_json();
        let tasks: Vec<TaskEntry> = serde_json::from_str(&json).unwrap();
        assert_eq!(tasks.len(), 1);
        assert_eq!(tasks[0].uuid, "u1");
        assert_eq!(tasks[0].tags[0], "a");

        let task1_update = TaskEntry {
            content: "Task 1 Updated".to_string(),
            updated_at: 200,
            ..task1.clone()
        };
        doc.update_task(
            "u1".to_string(),
            serde_json::to_string(&task1_update).unwrap(),
        );

        let json = doc.get_all_tasks_json();
        let tasks: Vec<TaskEntry> = serde_json::from_str(&json).unwrap();
        assert_eq!(tasks[0].content, "Task 1 Updated");
        assert_eq!(tasks[0].updated_at, 200);

        doc.delete_task("u1".to_string());
        let json = doc.get_all_tasks_json();
        let tasks: Vec<TaskEntry> = serde_json::from_str(&json).unwrap();
        assert_eq!(tasks.len(), 0);
    }
}
