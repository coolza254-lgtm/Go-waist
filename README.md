# Go waist

แอป Android ส่วนตัวสำหรับบันทึกการวิ่ง (นำเข้าจากภาพสกรีนช็อต Samsung Health) และการฝึก Bodyweight
พร้อมตารางซ้อม เป้าหมาย และรางวัลที่ตั้งเอง ข้อมูลทั้งหมดอยู่ในเครื่อง ไม่มีบัญชี ไม่มีคลาวด์ ไม่ขอสิทธิ์อินเทอร์เน็ต

## ดาวน์โหลด

APK ล่าสุด: [GoWaist.apk](../../releases/latest/download/GoWaist.apk) (Android 8.0 ขึ้นไป)
ทุกเวอร์ชันอยู่ที่หน้า [Releases](../../releases)

## ฟีเจอร์หลัก

- **วิ่ง**: นำเข้าผลวิ่งจากภาพ (OCR ในเครื่องด้วย ML Kit, อ่านตัวเลข+หน่วย ใช้ได้ทั้งภาษาไทย/อังกฤษ, km/mi)
  พร้อมหน้าตรวจแก้ ไฮไลต์ค่าที่ไม่มั่นใจ เตือนเพซไม่ตรง และกันบันทึกซ้ำ; ประวัติ ค้นหา แท็ก สถิติ PB กราฟ ปฏิทิน
- **Bodyweight**: คลังท่า 67 ท่า, สายความก้าวหน้า 6 สาย, Template, บันทึกเซ็ตเร็ว (+/−, ทำซ้ำแตะเดียว, RPE/RIR),
  ตัวจับเวลาพัก/ค้าง ที่ทำงานตอนปิดจอ, Superset, EMOM/AMRAP/Tabata, กู้คืนเซสชันค้าง, สถิติละเอียด
  (PB, 1RM Epley, volume, muscle heatmap, Push:Pull), คำแนะนำ progression / plateau / deload แบบ rule-based
- **แผน**: แผนสำเร็จรูป (5K/10K/ฮาล์ฟ, Full Body, PPL, วิ่ง+BW), ตารางกำหนดเอง, ปรับแผนอัตโนมัติ,
  เตือนโหลด (ขาหนักก่อน Long run, ไม่มีวันพัก, โหลดพุ่ง), จับคู่กับกิจกรรมจริง, แจ้งเตือนวันซ้อม
- **เป้าหมาย & รางวัล**, เหรียญตรา, Body Metrics, สำรอง/กู้คืน JSON, ส่งออก CSV, Widget หน้าจอหลัก

## โครงสร้างโปรเจกต์

- `core/` — Kotlin ล้วน: OCR parser, สถิติ, กฎ progression/แผน/เป้าหมาย, ข้อมูลตั้งต้น (มี unit test)
- `app/` — Jetpack Compose + Material 3, Hilt, Room (มี migration + schema), DataStore, WorkManager, Glance

## Build

```
./gradlew :core:test :app:testDebugUnitTest :app:assembleRelease
```

CI (`.github/workflows/build.yml`) รันเทสต์ สร้าง APK และสร้าง GitHub Release ทุกครั้งที่ push

### ลายเซ็น APK

ค่าเริ่มต้น CI ใช้ debug key ที่เก็บใน Actions cache ถ้าต้องการกุญแจถาวร (อัปเดตทับได้ตลอด)
ให้เพิ่ม repository secrets: `GOWAIST_KEYSTORE_BASE64` (keystore แบบ base64), `GOWAIST_KEYSTORE_PASSWORD`,
`GOWAIST_KEY_ALIAS`, `GOWAIST_KEY_PASSWORD` — หลังเปลี่ยนกุญแจครั้งแรกต้องถอนแอปเดิมก่อน (สำรองข้อมูลก่อน!)
