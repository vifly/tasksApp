use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use yrs::updates::decoder::Decode;
use yrs::{Any, Array, ArrayRef, Doc, MapRef, Out, ReadTxn, StateVector, Transact, Update};

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

        serde_json::to_string(&tasks).unwrap_or_else(|_| "[]".to_string())
    }

    pub fn restore_from_json(&self, json: String) {
        let tasks: Vec<TaskEntry> = serde_json::from_str(&json).unwrap_or_default();
        let mut txn = self.doc.transact_mut();

        let len = self.tasks_array.len(&txn);
        if len > 0 {
            self.tasks_array.remove_range(&mut txn, 0, len);
        }

        for task in tasks {
            Self::append_task_internal(&mut txn, &self.tasks_array, task);
        }
    }

    pub fn add_task(&self, json: String) {
        if let Ok(task) = serde_json::from_str::<TaskEntry>(&json) {
            let mut txn = self.doc.transact_mut();
            Self::insert_task_internal(&mut txn, &self.tasks_array, 0, task);
        }
    }

    pub fn update_task(&self, uuid: String, json: String) {
        if let Ok(new_task) = serde_json::from_str::<TaskEntry>(&json) {
            let mut txn = self.doc.transact_mut();
            // Using Any::Map means we replace the whole entry
            // This is "Task-Level LWW" (Last Write Wins)
            if let Some((index, _)) = self.find_task_index_by_uuid(&txn, &uuid) {
                // Remove old
                self.tasks_array.remove(&mut txn, index);
                // Insert new at same position
                let any_task = Self::task_to_any(new_task);
                self.tasks_array.insert(&mut txn, index, any_task);
            }
        }
    }

    pub fn delete_task(&self, uuid: String) {
        let mut txn = self.doc.transact_mut();
        if let Some((index, _)) = self.find_task_index_by_uuid(&txn, &uuid) {
            self.tasks_array.remove(&mut txn, index);
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
                        // Clone the map to return it? Or just index.
                        // We return clone of keys to satisfy borrow checker if needed,
                        // but here we just need index mostly.
                        // The map in Any::Map is Arc<HashMap>, so clone is cheap.
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

        // 1. Add Task
        let task1 = TaskEntry {
            uuid: "u1".to_string(),
            content: "Task 1".to_string(),
            is_pinned: false,
            created_at: 100,
            updated_at: 100,
            tags: vec!["a".to_string()],
        };
        doc.add_task(serde_json::to_string(&task1).unwrap());

        // Verify
        let json = doc.get_all_tasks_json();
        println!("DEBUG JSON: {}", json);
        let tasks: Vec<TaskEntry> = serde_json::from_str(&json).unwrap();
        assert_eq!(tasks.len(), 1);
        assert_eq!(tasks[0].uuid, "u1");
        assert_eq!(tasks[0].tags[0], "a");

        // 2. Update Task
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

        // 3. Delete Task
        doc.delete_task("u1".to_string());
        let json = doc.get_all_tasks_json();
        let tasks: Vec<TaskEntry> = serde_json::from_str(&json).unwrap();
        assert_eq!(tasks.len(), 0);
    }

    #[test]
    fn test_sync() {
        let doc_a = TaskDocument::new();
        let doc_b = TaskDocument::new();

        let task = TaskEntry {
            uuid: "sync_1".to_string(),
            content: "Sync Me".to_string(),
            is_pinned: true,
            created_at: 123,
            updated_at: 123,
            tags: vec![],
        };
        doc_a.add_task(serde_json::to_string(&task).unwrap());

        // Sync A -> B
        let update = doc_a.get_update();
        doc_b.apply_update(update);

        // Verify B has the task
        let json_b = doc_b.get_all_tasks_json();
        let tasks_b: Vec<TaskEntry> = serde_json::from_str(&json_b).unwrap();
        assert_eq!(tasks_b.len(), 1);
        assert_eq!(tasks_b[0].content, "Sync Me");
        assert!(tasks_b[0].is_pinned);
    }
}
