use log::{debug, error, info};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;
use yrs::updates::decoder::Decode;
use yrs::{Any, Array, ArrayRef, Doc, Out, ReadTxn, StateVector, Transact, Update};

uniffi::setup_scaffolding!();

#[uniffi::export(callback_interface)]
pub trait Logger: Send + Sync {
    fn log(&self, level: i32, tag: String, msg: String);
}

struct ProxyLogger {
    callback: Box<dyn Logger>,
}

impl log::Log for ProxyLogger {
    fn enabled(&self, _metadata: &log::Metadata) -> bool {
        true
    }

    fn log(&self, record: &log::Record) {
        let level = match record.level() {
            log::Level::Error => 6, // ANDROID_LOG_ERROR
            log::Level::Warn => 5,  // ANDROID_LOG_WARN
            log::Level::Info => 4,  // ANDROID_LOG_INFO
            log::Level::Debug => 3, // ANDROID_LOG_DEBUG
            log::Level::Trace => 2, // ANDROID_LOG_VERBOSE
        };
        self.callback
            .log(level, "RustSync".to_string(), format!("{}", record.args()));
    }

    fn flush(&self) {}
}

#[uniffi::export]
pub fn init_logger(callback: Box<dyn Logger>) {
    let logger = ProxyLogger { callback };
    let static_logger = Box::leak(Box::new(logger));
    let _ = log::set_logger(static_logger);
    log::set_max_level(log::LevelFilter::Debug);
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct TaskEntry {
    pub uuid: String,
    pub content: String,
    pub is_pinned: bool,
    pub created_at: i64,
    pub updated_at: i64,
    pub tags: Vec<String>,
    pub custom_sort_order: i64,
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
                let custom_sort_order = Self::get_i64_from_any(map.get("custom_sort_order"));

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
                    custom_sort_order,
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
        if let Ok(task) = serde_json::from_str::<TaskEntry>(&json) {
            let uuid = task.uuid.clone();
            let mut txn = self.doc.transact_mut();

            if let Some((index, _)) = self.find_task_index_by_uuid(&txn, &uuid) {
                info!("add_task: UUID {} already exists, treating as update", uuid);
                // Remove old and insert new at the same index to update
                self.tasks_array.remove(&mut txn, index);
                let any_task = Self::task_to_any(task);
                self.tasks_array.insert(&mut txn, index, any_task);
            } else {
                // Insert new at the beginning
                Self::insert_task_internal(&mut txn, &self.tasks_array, 0, task);
                debug!("add_task success for {}", uuid);
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
        map.insert(
            "custom_sort_order".to_string(),
            Any::BigInt(task.custom_sort_order),
        );

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

    struct MockLogger;
    impl Logger for MockLogger {
        fn log(&self, _level: i32, _tag: String, _msg: String) {}
    }

    #[test]
    fn test_task_crud() {
        // init_logger(Box::new(MockLogger)); // Optional for tests
        let doc = TaskDocument::new();

        let task1 = TaskEntry {
            uuid: "u1".to_string(),
            content: "Task 1".to_string(),
            is_pinned: false,
            created_at: 100,
            updated_at: 100,
            tags: vec!["a".to_string()],
            custom_sort_order: 500,
        };
        doc.add_task(serde_json::to_string(&task1).unwrap());

        let json = doc.get_all_tasks_json();
        let tasks: Vec<TaskEntry> = serde_json::from_str(&json).unwrap();
        assert_eq!(tasks.len(), 1);
        assert_eq!(tasks[0].uuid, "u1");
        assert_eq!(tasks[0].tags[0], "a");
        assert_eq!(tasks[0].custom_sort_order, 500);

        let task1_update = TaskEntry {
            content: "Task 1 Updated".to_string(),
            updated_at: 200,
            custom_sort_order: 1000,
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
        assert_eq!(tasks[0].custom_sort_order, 1000);

        doc.delete_task("u1".to_string());
        let json = doc.get_all_tasks_json();
        let tasks: Vec<TaskEntry> = serde_json::from_str(&json).unwrap();
        assert_eq!(tasks.len(), 0);
    }
}
