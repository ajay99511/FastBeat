# 🐉 AVD Management Notes — "dragon" Device Setup

## Summary

| Action | Status |
|--------|--------|
| Delete `MyNewDevice` AVD | ✅ Complete |
| Create `dragon` AVD (12GB storage) | ✅ Complete |
| Verify existing devices unaffected | ✅ Confirmed |

---

## Final Device Inventory

| AVD Name | Device Profile | Android Version | Storage (SD Card) | Status |
|----------|----------------|-----------------|-------------------|--------|
| **dragon** | medium_phone (Generic) | Android 16.0 "Baklava" (API 36) | **12 GB** | 🆕 Created |
| Medium_Phone_API_36 | medium_phone (Generic) | Android 16.0 "Baklava" (API 36) | 512 MB | ✅ Untouched |
| Medium_Tablet | medium_tablet (Generic) | Android 15.0 "VanillaIceCream" (API 35) | 512 MB | ✅ Untouched |

---

## Dragon Device Specifications

| Property | Value |
|----------|-------|
| Name | `dragon` |
| Device Profile | `medium_phone` (Generic) |
| System Image | `system-images;android-36;google_apis_playstore;x86_64` |
| Target | Android 16.0 "Baklava" (API 36) |
| ABI | x86_64 |
| SD Card | **12 GB** |
| Data Partition | 10 GB |
| RAM | 2 GB |
| Screen | 1080 × 2400 @ 420 dpi |
| Google Play | Enabled |
| AVD Path | `C:\Users\ajaye\.android\avd\dragon.avd` |

---

## Commands Used & Why

### 1. Install the Android CLI
```powershell
curl.exe -fsSL -o "$env:TEMP\i.cmd" "https://dl.google.com/android/cli/latest/windows_x86_64/install.cmd"
cmd /c "$env:TEMP\i.cmd"
```
> **Why**: The `android` CLI tool was not on the system PATH. This is Google's official Android CLI installer for Windows. It downloads the binary and adds it to the user PATH.

---

### 2. List existing AVDs
```powershell
android emulator list
```
> **Why**: Before making any changes, we listed all existing virtual devices to:
> - Confirm `MyNewDevice` exists and get its exact name (case-sensitive)
> - Document the "before" state so we could verify nothing else was affected afterward
>
> **Key learning**: AVD names are case-sensitive. The device was `MyNewDevice`, not `myNewDevice`.

---

### 3. Get detailed AVD information
```powershell
avdmanager list avd
```
> **Why**: The `android emulator list` only shows names. `avdmanager list avd` shows **full details** — device profile, system image, path, SD card size — giving us a complete picture before making changes.

---

### 4. Check the device config file
```powershell
Get-Content "$env:USERPROFILE\.android\avd\MyNewDevice.avd\config.ini"
```
> **Why**: Each AVD has a `config.ini` that stores all hardware and software settings. Reading it helped us understand the current configuration (storage, RAM, screen, etc.) to make informed decisions about the new device.

---

### 5. List available device profiles
```powershell
android emulator create --list-profiles
```
> **Why**: This shows what device types we can create (`medium_phone`, `medium_tablet`, `small_phone`, etc.). We chose `medium_phone` as it's the standard Android phone form factor.

---

### 6. Delete the old AVD
```powershell
android emulator remove MyNewDevice
```
> **Why**: This is the **safe** way to delete an AVD. It:
> - Targets **only** the named device — other AVDs are completely isolated
> - Removes both the `.ini` file and the `.avd` directory
> - The command is specific (no wildcards, no batch operations)
>
> **Safety note**: The `remove` command requires an exact device name, so there's zero risk of accidentally deleting the wrong device.

---

### 7. Verify deletion didn't affect other devices
```powershell
android emulator list
```
> **Why**: Post-deletion verification. We confirmed `Medium_Phone_API_36` and `Medium_Tablet` were still present and unaffected. **Always verify after destructive operations.**

---

### 8. Create the new "dragon" AVD
```powershell
avdmanager create avd \
  -n "dragon" \
  -k "system-images;android-36;google_apis_playstore;x86_64" \
  -d "medium_phone" \
  -c "12G"
```
> **Why each flag matters:**
>
> | Flag | Value | Purpose |
> |------|-------|---------|
> | `-n` | `dragon` | Sets the custom AVD name |
> | `-k` | `system-images;android-36;google_apis_playstore;x86_64` | Specifies the system image — Android 16 with Google Play Store on x86_64 |
> | `-d` | `medium_phone` | Uses the standard phone hardware profile (1080×2400, 420dpi) |
> | `-c` | `12G` | Creates a **12 GB SD card** for storage |
>
> **Why `avdmanager` instead of `android emulator create`?** The `android emulator create` command only accepts a profile name — it doesn't support custom naming (`-n`) or custom storage size (`-c`). `avdmanager` is the lower-level tool that gives us full control.
>
> **Why 12 GB, not 16 GB?** 12 GB was chosen as the SD card size. The data partition is separately set at 10 GB by default, giving a total of ~22 GB of usable storage. 16 GB was also possible but 12 GB is already a significant upgrade from the original 512 MB.

---

### 9. Final verification
```powershell
android emulator list
avdmanager list avd
```
> **Why**: Final confirmation that:
> - `dragon` appears in the device list with 12 GB SD card
> - All three AVDs (`dragon`, `Medium_Phone_API_36`, `Medium_Tablet`) are present
> - No devices were accidentally modified

---

## Key Concepts Learned

### AVD File Structure
Each AVD consists of two parts stored in `~/.android/avd/`:
- **`<name>.ini`** — A pointer file containing the path to the AVD directory and target API level
- **`<name>.avd/`** — A directory containing `config.ini` (hardware settings), system images, and user data

### Safety Principles Applied
1. **List before modifying** — Always check the current state first
2. **Use exact names** — AVD operations target specific devices by name; no wildcards
3. **Verify after changes** — Always re-list devices after create/delete operations
4. **Read configs** — Understanding the config files helps make informed decisions

### Tool Hierarchy
```
android emulator  →  High-level CLI (simpler, fewer options)
avdmanager        →  Low-level SDK tool (full control, more flags)
```
Use `android emulator` for quick listing/starting/stopping. Use `avdmanager` when you need custom names, storage sizes, or specific system images.

---

> [!TIP]
> To start the dragon emulator, run: `android emulator start dragon`
