use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

#[derive(Clone, Default)]
pub struct AppAliveness {
    pub alive: Arc<AtomicBool>,
    pub ready: Arc<AtomicBool>,
}

impl AppAliveness {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn set_alive(&self, val: bool) {
        self.alive.store(val, Ordering::Relaxed);
    }

    pub fn set_ready(&self, val: bool) {
        self.ready.store(val, Ordering::Relaxed);
    }

    pub fn is_alive(&self) -> bool {
        self.alive.load(Ordering::Relaxed)
    }

    pub fn is_ready(&self) -> bool {
        self.ready.load(Ordering::Relaxed)
    }
}
