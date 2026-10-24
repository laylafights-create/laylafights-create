# Zhwii Client — bản port Minecraft 26.2 (Fabric)

Client HUD / PvP tiếng Việt, port từ bản Forge 1.8.9 sang **Minecraft 26.2** dùng **Fabric** (Loom 1.17, Fabric API 0.152.2+26.2, Java 25).
Mod chỉ chạy phía client.

> **Trạng thái:** đây là bản port **giai đoạn 1 (phần lõi)**. Mã được viết dựa trên tài liệu Fabric cho 26.x
> nhưng **chưa được biên dịch hay chạy thử** (môi trường viết mã không tải được Minecraft 26.2). Lần build đầu tiên
> có thể có vài lỗi tên hàm/lớp do 26.2 đổi API — xem mục "Chỗ dễ lỗi" bên dưới.

## Giai đoạn 9 — mới (Hotbar Animation, Enemy Detector, Chat Bar)

Chưa biên dịch / chạy thử. Mọi mixin mới đều `require = 0`: sai tên ở 26.2 thì tính năng đó không có tác dụng, không crash.

- **Hotbar Animation — animation kho đồ** (`HotbarAnimationModule` + `mixin/ContainerScreenMixin`): khung chọn trượt bằng lò xo giữa các ô, giãn theo vận tốc, quầng sáng, gợn sóng + vệt sáng quét khi trỏ vào ô mới, item đang trỏ phóng to / lắc / nổi lên có bóng, pop khi item đổi chỗ. Áp dụng cho mọi `AbstractContainerScreen`. Đọc `leftPos/topPos/hoveredSlot` bằng reflection (sai tên thì tắt phần này).
- **Enemy Detector:**
  - Viền kẻ địch là **silhouette thật của game** (glowing, xuyên tường) qua `MinecraftGlowMixin` (`shouldEntityAppearGlowing`) + `EntityGlowColorMixin` (`getTeamColor`): rainbow, màu "gần nhất", Show Invisible hoạt động. Độ dày viền do game quyết định nên **Line Width / Glow chỉ áp dụng cho "Khung hộp 2D"** (tuỳ chọn mới, mặc định tắt; bật nếu mixin không áp dụng). Through Walls tắt thì chỉ viền khi có đường nhìn thẳng. Không có Smoothness (không còn stencil).
  - Port đủ thuật toán chọn mục tiêu của 1.8.9: giữ chỗ (sticky), thời gian ân hạn, ép "gần nhất" lên đầu, chờ sảnh Hypixel, làm mới "gần nhất" mỗi khung hình, **Debug (chat)**, **Độ dày viền mũi tên**.
- **Chat Bar** (`gui/ChatView`, `gui/ChatPositionScreen`, `mixin/ChatComponentMixin`, `ChatScreenMixin`):
  - Khung chat riêng thay chat vanilla: khung kính bo góc, viên bo góc ôm chữ khi đóng chat, fade, trượt khi có tin mới, thanh cuộn mảnh (lăn chuột khi mở chat), khoảng cách dòng, rộng khung, nền vừa chữ / ẩn nền, tô màu tin theo đội.
  - **Avatar trước tin** (cỡ + lệch X/Y chỉnh được); **tin giết người** viết lại thành `[avt] Killer giết [avt] Victim (FINAL KILL!)`.
  - **Kéo thả khung chat** (nút "Kéo thả vị trí chat…" trong bảng tuỳ chỉnh; lăn chuột trên khung để đổi độ rộng).
  - Thanh nhập: viền màu accent, bộ đếm ký tự x/100, độ rộng tuỳ chỉnh.
  - Cách hoạt động: mixin bắt mọi tin vào `ChatComponent#addMessage`; chat vanilla chỉ bị ẩn **sau khi đã bắt được tin đầu tiên**, nên nếu mixin sai tên thì chat vanilla vẫn hiện như cũ. Tin trước lần bắt đầu tiên (vd lúc vừa vào thế giới) không hiện trong khung mới.
  - **Chưa có / khác bản gốc:** animation gõ / xoá chữ, đổi vị trí & độ mờ thanh nhập (vanilla tự vẽ nền thanh nhập), icon giường (bản gốc cũng đã bỏ), nút "[Ẩn gốc]", click link / hover trong khung mới (vanilla không còn vẽ nên không click được), màu đội theo giáp da. Số dòng hiển thị: 7 khi đóng, 20 khi mở.
  - Chỗ dễ lỗi: `Options#chatScale()/chatOpacity()`, `GuiGraphicsExtractor#text(Font, FormattedCharSequence, ..)`, `Scoreboard#getPlayersTeam`, tên `ChatComponent#addMessage` / `extractRenderState`.

## Giai đoạn 8 — mới

- **HudEditorScreen port 1:1** (`gui/HudEditorScreen.java`, 125 → ~560 dòng): bảng cài đặt riêng cho từng HUD (kích cỡ +/-, công tắc, chế độ, màu, thanh trượt, nút riêng của module, đặt lại), kéo bảng bằng tiêu đề (chuột phải = về vị trí tự động), thanh cuộn kéo được, hít vào mép / giữa màn hình kèm đường dẫn, phím mũi tên để nhích (Shift = 5px), dấu X để tắt HUD, animation trượt vào, nút "Quay lại menu".
- **Chưa kiểm chứng:** chưa biên dịch với Minecraft 26.2. Chỗ dễ lỗi: `KeyMapping#matches(KeyEvent)`, `KeyEvent#modifiers()`, `SoundEvents.UI_BUTTON_CLICK.value()`.

### Còn lại so với bản 1.8.9 (số dòng gốc → hiện tại)

Potion (291 → 141), Zoom (289 → 136), Animations (116 → 49), Server IP (86 → 22), Scoreboard (72 → 17), Item Physics, Far Horizons (LOD), rung block, animation kho đồ.

## Giai đoạn 7 — mới

- **Menu SAO đầy đủ** (`gui/MenuScreen.java`, viết lại từ bản 2187 dòng của 1.8.9): thẻ nhân vật → cột nút tròn (nhóm + nút bánh răng "Thao tác") → danh sách tính năng → bảng tuỳ chỉnh 3 cột; hộp xác nhận bật/tắt/đăng xuất, nền hạt Aincrad, yêu thích (tim), phím số 1–9/0, ←/→ đổi tính năng, chuột phải bật/tắt nhanh, thanh cuộn kéo được, animation mở/đóng. Hàng tuỳ chỉnh tự vẽ (không dùng widget): công tắc, chế độ, màu (chuột trái/phải đổi), thanh trượt, nút Đặt lại.
  - **Khác bản gốc:** thẻ nhân vật vẽ **mặt** (`Draw.head`) thay vì mô hình 3D xoay; nền mờ do game tự vẽ (không có post-shader `menu_blur`).
- **AnimeFx** (`gui/AnimeFx.java`): nổ sao + sóng tròn khi bấm, vệt sáng quét qua hàng khi rê chuột; cánh hoa, tia sáng, sao lấp lánh, vạch tốc độ cũng đã port (bản gốc không gọi tới). Khác 1.8.9: không có trộn cộng sáng và không có màu chuyển theo đỉnh, nên quầng/dải màu là các lớp alpha.
- **ProfileScreen** (`gui/ProfileScreen.java`): màn nhập/xuất riêng (danh sách hồ sơ, Nhập / Dán / Xuất / Sao chép mã / Mở thư mục, bấm đúp hoặc Enter để nhập). Nút "Nhập / Xuất" trong menu mở màn này.
- **`Module.Action` + `consumeUiDirty()`** đã có lại để module thêm nút riêng vào bảng tuỳ chỉnh.
- **Zoom lăn chuột** (`MouseHandlerMixin` + `ZoomModule#handleScroll`): khi đang zoom, lăn chuột đổi FOV đích (30–110, có gia tốc, đảo chiều được) và không đổi ô hotbar. Sai tên `MouseHandler#onScroll` thì chỉ mất tính năng này.
- **Chưa kiểm chứng:** như các giai đoạn trước, mã chưa được biên dịch/chạy thử (không có Minecraft 26.2 trong môi trường viết mã). Chỗ dễ lỗi mới: `KeyMapping#matches(KeyEvent)`, `Screen#tick()`, `Player#getItemBySlot`, `KeyboardHandler#getClipboard/setClipboard`.

### Vẫn chưa port (không viết mù được)

- **Item Physics** và **Far Horizons (LOD)**: cả hai dựa vào vẽ GL cố định / Tessellator của 1.8.9; ở 26.2 cần mixin vào `ItemEntityRenderer` và pipeline Blaze3D mới. Chữ ký thật chỉ biết được khi có log build.
- Chat Bar: avatar / giường team trước tin, khung kính kéo thả, thanh nhập tuỳ biến.
- Rung block khi đào, animation mở kho đồ / rương, mô hình 3D trong thẻ nhân vật.

## Giai đoạn 6 — mới

- **Sửa lỗi chuyển màn hình 26.2:** `Minecraft#setScreen` đã đổi thành `setScreenAndShow` và field `screen` dời sang `Gui` — `Compat` giờ thử cả hai (bản phase 5 sẽ lỗi khi mở menu).
- **Hide Armor, Clean GUIs, No Block Particles** (qua mixin, `zhwii.mixins.json`). Mọi inject đều `require = 0` và config mixin `required: false`: nếu 26.2 đổi tên hàm thì tính năng đó **không có tác dụng** chứ game không crash. Kiểm tra log có dòng `Mixin apply ... failed` hoặc `@Inject ... was not found`.
  - Hide Armor: xoá ô giáp trong render state của người chơi local (reflection `headEquipment/chestEquipment/legsEquipment/feetEquipment`).
  - Clean GUIs: huỷ `Screen#extractBackground` / `extractTransparentBackground` với màn hình container. Chưa có animation mở kho đồ.
  - No Block Particles: huỷ `ParticleEngine#destroy` / `crack`. Chưa có hiệu ứng rung block.
- **Nền tảng cho Menu SAO (mới, chưa dùng tới):** đã port `gui/Skin.java` (bộ vẽ SAO: cửa sổ, nút tròn, hàng, công tắc, nút), `gui/SaoIcons.java`, `gui/Icons.java` (icon vẽ bằng đường / cung / đa giác) và thêm `Draw.arc`, `Draw.poly`. `MenuScreen` vẫn là bản rút gọn của phase 5; bản SAO gốc (2187 dòng, 3 cột kiểu cascade) sẽ port ở bước tiếp theo, cần thêm `Module.actions()` / `consumeUiDirty()` của bản Forge.
- **Chat Bar (giai đoạn 6):** port đủ phần logic của bản 1.8.9: khôi phục dấu tiếng Việt không dấu (viết tắt, Telex, Google Input Tools, ghép dấu), nhận diện tin tiếng Anh, tách tin dài thành tối đa 3 tin <= 100 ký tự, thử lại 3 lần, cache dịch ghi ra `config/zhwii_translate_cache.json`, và **nút VI>EN / EN>VI** bên phải ô nhập (ô nhập tự thu hẹp; lưu trong config `viToEn`). Nút dùng mixin `ChatScreenMixin` (không bắt buộc, sai tên thì mất nút).
  **Chưa có:** avatar / giường team trước tin, khung chat kính + kéo thả vị trí, thanh nhập tuỳ biến (viền, độ mờ, bộ đếm ký tự), animation tin nhắn — cần hook vào `ChatComponent` của 26.2.
- **Vẫn chưa port:** Item Physics, Far Horizons (LOD), Menu SAO đầy đủ, phần còn thiếu của Chat Bar, rung block. Cần viết lại theo renderer mới của 26.2 (`MultiBufferSource` đã bị xoá) — cần log build thật để làm chính xác.

## Đã port

| Nhóm | Module |
|---|---|
| HUD | FPS, Ping, Server Address (chưa có icon favicon), **Ẩn Scoreboard, Thông báo nhặt đồ** (giai đoạn 2) |
| Tiện ích | **Chat Bar** rút gọn (giai đoạn 3) |
| PvP | CPS, Keystrokes (bản rút gọn), Thông tin mục tiêu, Combo, Armor Status, Potion Status, **Aim Assist, Velocity, Bridge Assist** (giai đoạn 2), **Kill Board, Hotbar Animation, Hit Effect** (giai đoạn 3), **Enemy Detector, Bow Trajectory** (giai đoạn 4) |
| Hình ảnh / Tiện ích | **Block Outline** (giai đoạn 4), Zoom, Fullbright, Toggle Sprint, Animations (menu + HUD), Màu giao diện |
| Hệ thống | Menu (Right Shift), trình chỉnh vị trí HUD (kéo thả, lăn chuột đổi tỉ lệ, chuột phải reset), nhập/xuất hồ sơ `.cfg`, config tự lưu |

## Chưa port (làm ở giai đoạn sau)

Các phần dựa vào OpenGL cố định / Tessellator / hook sâu vào renderer của 1.8.9, cần viết lại bằng Blaze3D hoặc mixin:
Item Physics, Far Horizons (LOD),
Hide Armor (cần mixin), rung block khi đào (NoBlockParticles), kho đồ không nền đen, ProfileScreen, bản đầy đủ của Zoom / Keystrokes / Target Info / Menu SAO.

Ghi chú giai đoạn 2: Bridge Assist chỉ tự đổi ô hotbar khi hết block (chưa kéo block từ túi đồ). Velocity xử lý ở client tick
nên có thể chậm 1 tick so với bản Forge. Hide Scoreboard dùng `HudElementRegistry.replaceElement` (không cần mixin).

## Khác biệt so với bản 1.8.9

- **Zoom:** đổi FOV qua option của game nên bị giới hạn **30–110**; không có lăn chuột chỉnh mức zoom.
- **Fullbright:** gắn hiệu ứng Night Vision vô hạn phía client (ẩn icon/hạt) thay vì chỉnh gamma.
- **CPS:** đo cạnh lên của nút chuột mỗi frame (không còn sự kiện chuột kiểu Forge).
- **Keystrokes:** chỉ còn hiệu ứng pha màu mượt (bỏ lò xo, tia lửa, sóng).
- File config: `config/zhwii.cfg`; hồ sơ: `config/zhwii-profiles/`. Cấu hình cũ của Forge không tự chuyển sang.

## Phím mặc định

| Phím | Chức năng |
|---|---|
| `Right Shift` | Mở menu |
| `C` | Zoom |

Đổi phím trong *Options → Controls → Key Binds → Zhwii Client*.

## Build

Cần **JDK 25** và **Gradle 9.x** (repo không kèm wrapper):

```bash
gradle wrapper --gradle-version 9.5.1   # tuỳ chọn, tạo gradlew
gradle build                            # jar nằm ở build/libs/ZhwiiClient-26.2-3.0.0.jar
```

Chạy thử trong môi trường dev: `gradle runClient`. Workflow GitHub Actions (`.github/workflows/build.yml`) tự build và đính kèm jar.

## Chỗ dễ lỗi khi build lần đầu (26.2 đổi API)

Mọi thay đổi theo hướng "đổi tên" đều gói gọn ở vài file nên sửa nhanh:

- `Compat.java` — chuyển màn hình (`setScreen` đã dời từ `Minecraft` sang `Gui` ở 26.2), đọc FPS; dùng reflection nên chịu được cả hai cách.
- `gui/Draw.java` — mọi lệnh vẽ (`fill`, `text`, `item`, `enableScissor`, `pose()`); nếu `GuiGraphicsExtractor#item` đổi tên thì chỉ sửa ở đây.
- `MenuScreen` / `HudEditorScreen` — chữ ký sự kiện chuột/phím (`MouseButtonEvent`, `KeyEvent`).
- `PotionModule` — `blitSprite` và tên sprite hiệu ứng; `ZoomModule` — `Options#fov()` / `sensitivity()`.

Tham khảo: [Porting to 26.2](https://docs.fabricmc.net/develop/porting/), [Fabric for Minecraft 26.2](https://fabricmc.net/2026/06/15/262.html).

## Lưu ý

Một số tính năng có thể bị server cấm. Người dùng tự chịu trách nhiệm khi sử dụng. Giữ ghi công tác giả gốc khi phân phối lại.

## Giai đoạn 3 — ghi chú

- **Kill Board:** đọc chat qua `ClientReceiveMessageEvents.ALLOW_GAME` (phase sớm, nên vẫn đếm kill dù Chat Bar ẩn tin), ẩn bossbar bằng `replaceElement(BOSS_BAR)`.
  Vẽ mặt người chơi qua `Draw.head` (reflection tìm `PlayerFaceExtractor`/`PlayerFaceRenderer`; không tìm được thì vẽ chữ cái đầu).
- **Chat Bar (rút gọn):** lọc chỉ còn tin người chơi + sự kiện Bedwars, dịch tin nhận → tiếng Việt, tin gửi → tiếng Anh (Google Translate, chạy nền, cache RAM).
  Chưa có: avatar/giường trước tin nhắn, khung chat kính, thanh nhập tuỳ biến, khôi phục dấu tiếng Việt, cache ra file. Chỉ bắt tin `GAME` (Hypixel); chat có chữ ký của server vanilla chưa xử lý.
- **Hotbar Animation:** thay hotbar vanilla (`replaceElement(HOTBAR)`); chưa có animation trong kho đồ; không còn chỉ báo tấn công của vanilla.
- **Hit Effect:** hạt được chiếu từ toạ độ thế giới ra màn hình rồi vẽ 2D (không còn quad 3D / cộng sáng), nên không bị block che và hơi lệch ở góc nhìn thứ 3.
- Các hàm có thể sai tên ở 26.2: `GuiGraphicsExtractor#itemDecorations`, `PlayerInfo#getSkin`, `Options#fov`, `LocalPlayer#getInventory().getSelectedSlot`.

## Giai đoạn 4 — ghi chú

- **Lớp phủ 3D vẽ 2D:** `gui/Proj.java` chiếu toạ độ thế giới ra màn hình (như `AnimeHit`); Bow Trajectory, Block Outline và Enemy Outline dùng chung. Hệ quả: tính theo mắt camera + FOV trong options (lệch ở góc nhìn thứ 3 / khi FOV đổi do chạy), không có chiều sâu thật.
- **Enemy Detector:** bảng danh sách, mũi tên hướng, báo âm thanh, avatar (qua `Draw.head`) đã port đủ. "Enemy Outline" kiểu stencil silhouette của 1.8.9 được thay bằng **khung hộp 2D + glow + rainbow**; Through Walls tắt thì ẩn khung khi tia nhìn tới người chơi bị block chặn.
- **Bow Trajectory:** dùng công thức mũi tên hiện đại (thêm vận tốc người bắn, cản 0.99, trọng lực 0.05). Không có tuỳ chọn Through Walls (luôn vẽ đè).
- **Block Outline:** viền vanilla vẫn còn bên dưới vì chưa có hook để huỷ; Through Walls tắt thì các đoạn bị block khác che sẽ được ẩn.
- Hàm có thể sai tên ở 26.2: `BowItem#getPowerForTime`, `ItemStack#getUseDuration(LivingEntity)`, `LocalPlayer#getUseItemRemainingTicks`, `Minecraft#hitResult`, `Level#getMinY`, `SimpleSoundInstance#forUI`, `Player#getGameProfile().name()`.

### Vẫn chưa port (cần mixin hoặc viết lại renderer, không kiểm chứng được nếu không build)

Item Physics, Far Horizons (LOD), Hide Armor, rung block / NoBlockParticles, kho đồ không nền đen (CleanGuis), ProfileScreen, hiệu ứng SAO (chết / vỡ mảnh / icon), bản đầy đủ của Zoom (lăn chuột) / Keystrokes / Target Info / Menu SAO, và các phần còn thiếu của Chat Bar.

## Giai đoạn 5 — ghi chú

- **Keystrokes:** đã port đủ bản gốc (lò xo nhún, nền dâng lên, tia lửa, sóng, vệt sáng, xung khi nhả, phím bên cạnh nhún theo, CPS chạy mượt "4 LMB", hiện lần lượt từng phím).
- **Target Info:** thẻ đầy đủ như bản gốc (avatar, tên 4 kiểu, khoảng cách, % máu / số máu, thanh máu có vệt tụt, khung góc, màu máu 4 kiểu). Người chơi vẽ bằng mặt (`Draw.head`); mob vẽ mô hình qua `InventoryScreen#extract...FollowsMouse` (tìm bằng reflection, không được thì hiện chữ cái đầu). Vẫn giữ thêm mục tiêu "đòn vừa đánh" của bản 26.2.
- **Hiệu ứng SAO** (`SaoFx`, `gui/SaoDeath`, `gui/SaoDeathScreen`, `gui/SaoShatter`): tiếng chết, tiếng cảnh báo máu thấp (< 5 tim, lặp liền mạch), màn hình "YOU ARE DEAD" thay `DeathScreen` (nút Hồi sinh / Về màn hình chính), vỡ mảnh + tiếng chết khi mob / người chơi khác chết (kể cả bị xoá ngay như Hypixel). Âm thanh nằm ở `assets/zhwii/sounds*`. Mảnh vỡ vẽ 2D qua `Proj`; thân xác vanilla vẫn hiện (chưa có hook để ẩn). Bật / tắt trong `zhwii.cfg`: `deathFx`, `shatterFx`.
- **ProfileScreen:** không cần port riêng — menu 26.2 đã có sẵn nhập / xuất / sao chép / dán hồ sơ (`ProfileManager`).
- Hàm có thể sai tên ở 26.2: `AbstractSoundInstance` (constructor, `looping`, `attenuation`), `SoundManager#isActive`, `LocalPlayer#respawn`, `Level#entitiesForRendering`, `Minecraft#disconnect`.

### Vẫn chưa port

Item Physics (cần mixin vào `ItemEntityRenderer#submit`, chữ ký ở 26.2 mình chưa kiểm chứng được), Menu SAO (menu gốc 2187 dòng: màn hình home dạng vòng tròn, hero, icon SAO), phần còn thiếu của Chat Bar (avatar / giường trước tin, khung kính, thanh nhập tuỳ biến, khôi phục dấu tiếng Việt, cache file), Far Horizons, Hide Armor, NoBlockParticles, CleanGuis.
