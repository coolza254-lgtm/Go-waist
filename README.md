# Go waist

แอป Android ส่วนตัวสำหรับติดตามการวิ่งและพัฒนาการอย่างละเอียด จากภาพสรุปของ Samsung Health ที่นำเข้าหลังวิ่ง
ดีไซน์มินิมอลโทนครีม ข้อมูลทั้งหมดอยู่ในเครื่อง ไม่มีบัญชี ไม่มีคลาวด์ ไม่ขอสิทธิ์อินเทอร์เน็ต

## ดาวน์โหลด

APK ล่าสุด: [GoWaist.apk](../../releases/latest/download/GoWaist.apk) (Android 8.0 ขึ้นไป)
ทุกเวอร์ชันอยู่ที่หน้า [Releases](../../releases)

## ฟีเจอร์หลัก

- **นำเข้าการวิ่ง**: อ่านภาพสรุปจาก Samsung Health ด้วย OCR ในเครื่อง (ML Kit) ระยะ เวลา เพซ ชีพจร แคลอรี
  พร้อมหน้าตรวจแก้ ไฮไลต์ค่าที่ไม่มั่นใจ และกันบันทึกซ้ำ; ผูกกับรองเท้าคู่หลักอัตโนมัติ
- **ประวัติ**: ค้นหา แท็ก สถิติรายสัปดาห์/เดือน/ปี กราฟเพซ ปฏิทิน
- **พัฒนาการ**: VO2 Max รายวัน, ฟิตเนส/ความล้า/ความสด, คาดการณ์เวลาแข่ง, เพซฝึก, โซนชีพจร,
  ประสิทธิภาพการวิ่ง, น้ำหนักตัว (เฉลี่ย 7 วัน อัตราต่อสัปดาห์ BMI), เป้าหมายและสถิติส่วนตัว (PB)
- **รองเท้า**: ระยะสะสม ราคาต่อกิโลเมตร ความทนทาน (% ที่กรอกเอง) และระยะที่น่าจะใช้ได้อีก
- สำรอง/กู้คืน JSON, ส่งออก CSV, Widget หน้าจอหลัก

## โครงสร้างโปรเจกต์

- `core/` — Kotlin ล้วน: OCR parser, สถิติการวิ่ง, VO2 Max/โหลดการฝึก, เป้าหมาย, รองเท้า (มี unit test)
- `app/` — Jetpack Compose + Material 3, Hilt, Room (มี migration + schema), DataStore, Glance

## Build

```
./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease
```

CI (`.github/workflows/build.yml`) รันเทสต์ สร้าง APK และสร้าง GitHub Release ทุกครั้งที่ push

### ลายเซ็น APK

ค่าเริ่มต้น CI ใช้ debug key ที่เก็บใน Actions cache ถ้าต้องการกุญแจถาวร (อัปเดตทับได้ตลอด)
ให้เพิ่ม repository secrets: `GOWAIST_KEYSTORE_BASE64` (keystore แบบ base64), `GOWAIST_KEYSTORE_PASSWORD`,
`GOWAIST_KEY_ALIAS`, `GOWAIST_KEY_PASSWORD` — หลังเปลี่ยนกุญแจครั้งแรกต้องถอนแอปเดิมก่อน (สำรองข้อมูลก่อน!)
