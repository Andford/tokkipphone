// SPDX-License-Identifier: GPL-3.0-or-later
//! Tokkip shared between the phone and Tokkip PC: the phone's Tokkip app tells
//! Tokkip Phone on the phone, which sends it here in its device description
//! (`DeviceInfo::tokkip`); this writes it to `%APPDATA%\Tokkip\shared\
//! phone-tokkip.json` for Tokkip PC. Tokkip PC's own goes the other way: it
//! leaves `pc-tokkip.json` there, and this PC's description carries it to the
//! phones. The JSON is the Tokkip apps' (docs/protocol/tokkip.md); here it is
//! only checked for size and shape, and the newer one (`changedAt`) is kept.

use std::path::PathBuf;
use std::time::{Duration, SystemTime};

use tokkiphone_core::{DeviceKind, NodeEvent, PairedDevice};

/// What a device may send (the core carries no more).
const MAX_LEN: usize = tokkiphone_core::MAX_TOKKIP_LEN;
/// How often Tokkip PC's file is looked at.
const POLL: Duration = Duration::from_secs(3);

/// `%APPDATA%\Tokkip\shared` (`~/.config/Tokkip/shared` on Linux), where the
/// Tokkip apps leave each other notes.
fn shared_dir() -> Option<PathBuf> {
    Some(dirs::config_dir()?.join("Tokkip").join("shared"))
}

/// A Tokkip JSON of the right size and shape, and when it last changed.
fn parse(json: &str) -> Option<i64> {
    if json.is_empty() || json.len() > MAX_LEN {
        return None;
    }
    let value: serde_json::Value = serde_json::from_str(json).ok()?;
    let object = value.as_object()?;
    if object.get("v").and_then(serde_json::Value::as_i64) != Some(1) {
        return None;
    }
    object.get("changedAt").and_then(serde_json::Value::as_i64)
}

fn read(name: &str) -> Option<String> {
    let path = shared_dir()?.join(name);
    let meta = std::fs::metadata(&path).ok()?;
    if !meta.is_file() || meta.len() > MAX_LEN as u64 {
        return None;
    }
    std::fs::read_to_string(path).ok().filter(|json| parse(json).is_some())
}

/// Tokkip PC's Tokkip, for this PC's description (`None` without Tokkip PC).
pub fn pc_tokkip() -> Option<String> {
    read("pc-tokkip.json")
}

/// The newest Tokkip among the paired phones and tablets.
fn newest_phone_tokkip(devices: &[PairedDevice]) -> Option<(i64, String)> {
    devices
        .iter()
        .filter(|d| matches!(d.info.kind, DeviceKind::Phone | DeviceKind::Tablet))
        .filter_map(|d| d.info.tokkip.as_ref())
        .filter_map(|json| parse(json).map(|at| (at, json.clone())))
        .max_by_key(|(at, _)| *at)
}

/// Writes the phones' Tokkip for Tokkip PC when it's newer than what's there.
fn write_phone_tokkip(devices: &[PairedDevice]) {
    let Some((at, json)) = newest_phone_tokkip(devices) else { return };
    let current = read("phone-tokkip.json");
    if current.as_deref() == Some(json.as_str()) || current.as_deref().and_then(parse).is_some_and(|c| c > at)
    {
        return;
    }
    let Some(dir) = shared_dir() else { return };
    if std::fs::create_dir_all(&dir).is_err() {
        return;
    }
    // Whole or not at all: Tokkip PC never reads half a file.
    let tmp = dir.join("phone-tokkip.json.tmp");
    if std::fs::write(&tmp, json.as_bytes()).is_ok()
        && let Err(e) = std::fs::rename(&tmp, dir.join("phone-tokkip.json"))
    {
        tracing::warn!(error = %e, "can't leave the phone's Tokkip for Tokkip PC");
    }
}

/// A phone told us something new about itself: maybe its Tokkip.
pub fn on_event(event: &NodeEvent) {
    if matches!(event, NodeEvent::PeerInfoChanged { .. } | NodeEvent::DeviceAdded(_))
        && let Some(node) = crate::core_host::node()
    {
        write_phone_tokkip(&node.paired_devices().unwrap_or_default());
    }
}

/// Once the node runs: the phones' Tokkip as last heard, and a watch on
/// Tokkip PC's file that sends each change to the phones.
pub fn start() {
    if let Some(node) = crate::core_host::node() {
        write_phone_tokkip(&node.paired_devices().unwrap_or_default());
    }
    let _ = std::thread::Builder::new().name("tokkip-share".into()).spawn(|| {
        let mut seen: Option<SystemTime> = None;
        loop {
            let modified = shared_dir()
                .and_then(|d| std::fs::metadata(d.join("pc-tokkip.json")).ok())
                .and_then(|m| m.modified().ok());
            if modified != seen {
                seen = modified;
                if let Some(json) = pc_tokkip() {
                    crate::core_host::spawn(async move {
                        if let Some(node) = crate::core_host::node() {
                            node.update_tokkip(Some(json)).await;
                        }
                    });
                }
            }
            std::thread::sleep(POLL);
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    use tokkiphone_core::{DeviceId, DeviceInfo, LinkState};

    fn device(kind: DeviceKind, tokkip: Option<&str>) -> PairedDevice {
        PairedDevice {
            id: DeviceId([1; 32]),
            info: DeviceInfo {
                name: "Pixel".into(),
                kind,
                os: "android".into(),
                os_ver: "16".into(),
                model: None,
                accent: None,
                screen: None,
                install_id: None,
                tokkip: tokkip.map(str::to_owned),
            },
            paired_at: 0,
            link: LinkState::Offline { last_seen: None },
            can_wake: false,
            reconnect: true,
        }
    }

    #[test]
    fn only_a_tokkip_of_ours_counts() {
        assert_eq!(parse(r#"{"v":1,"changedAt":5,"look":{}}"#), Some(5));
        assert_eq!(parse(r#"{"v":2,"changedAt":5}"#), None);
        assert_eq!(parse(r#"{"v":1}"#), None);
        assert_eq!(parse("[1]"), None);
        assert_eq!(parse(&format!(r#"{{"v":1,"changedAt":1,"x":"{}"}}"#, "a".repeat(MAX_LEN))), None);
    }

    #[test]
    fn the_newest_phone_wins_and_pcs_dont_count() {
        let devices = [
            device(DeviceKind::Phone, Some(r#"{"v":1,"changedAt":10}"#)),
            device(DeviceKind::Tablet, Some(r#"{"v":1,"changedAt":30}"#)),
            device(DeviceKind::Laptop, Some(r#"{"v":1,"changedAt":99}"#)),
            device(DeviceKind::Phone, None),
        ];
        assert_eq!(newest_phone_tokkip(&devices).map(|(at, _)| at), Some(30));
        assert_eq!(newest_phone_tokkip(&devices[3..]), None);
    }
}
