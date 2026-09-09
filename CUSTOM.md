# 飞智手柄与 Sunshine 原生缩放定制版

上游：[Axixi2233/moonlight-android](https://github.com/Axixi2233/moonlight-android)。基于 `260906`（`df1c125f5979f3529014d023afcc9e971a79a056`），原上游许可证和署名保留。此版本不是上游官方发行。

## 定制功能

### 26.09.09-dpi4-async-exit（versionCode 504）

修复 Foundation Sunshine 异步退出时被 Moonlight 误报为 599：`/cancel` 只发送一次，应用列表立即恢复，后台在有限时间内只读查询主机是否完成清理。清理期间显示状态条；立即重新连接时在连接线程等待主机就绪，不阻塞界面。主机拒绝、网络错误或清理超时仍会显示真实错误。

### 26.09.09-dpi3-ui（versionCode 503）

电脑缩放入口移动到显示选项列表的帧率下方，复用灰色图标行、箭头和侧栏子页面。独立系统弹窗已移除。页面支持刷新、实际比例反馈和绿色已确认状态；沿用原有配对 HTTPS 与连续选择队列，不改变 Sunshine 配置。返回再进入会重新读取，旧页面的请求结果不会更新新页面。

云构建、签名和测试通过后仍需在设备确认横竖屏、返回导航、连续调节与断网提示。升级保持包名及签名不变。

- 黑武士 5 Pro 接收器（VID `37d7` / PID `2401`）输入兼容。
- 串流侧栏 → 显示 → 电脑缩放，通过 Foundation Sunshine 配对 HTTPS 接口直接调节 Windows DPI。
- 实际缩放查询、执行结果反馈、串行请求且保留最后一次选择。
- 对 Foundation Sunshine v2026.823 的错误 DPI 档位表作双向兼容；上游修正为标准档位后不再转换。

包名 `com.limelight.flydigi`，现有稳定版本号 502。这个定制面板仅允许主屏名称含 Zako 的虚拟显示器，避免更改物理桌面；不支持任意主机和任意显示器配置。启动/退出时的布局和 DPI 恢复由单独的电脑端脚本维护，不包含个人配置。

## 本地构建

使用 JDK 17、Android SDK 34、NDK `27.0.12077973` 和仓库 Gradle 8.7 wrapper。克隆时加 `--recurse-submodules`，不要删除子模块历史。

```
./gradlew -PincludeKishiHaptics=false -PincludeStereo3dAi=false testNonRootFlydigiUnitTest assembleNonRootFlydigi
```

未设置签名环境变量时仅生成未签名的 flydigi 包，不能拿它覆盖已安装版。需要签名时提供 `STREAM_SIGNING_STORE`、`STREAM_SIGNING_STORE_PASSWORD`、`STREAM_SIGNING_ALIAS`、`STREAM_SIGNING_KEY_PASSWORD`。不要上传密钥或把口令写进命令历史。

升级时可指定 `-PcustomVersionCode=503 -PcustomVersionName=26.09.06-custom.2`，版本号递增，包名和签名保持不变。

## GitHub 维护

`upstream` 指原作者，`origin` 指个人 Fork；个人改动维护在 `custom` 分支。

1. `git fetch upstream --tags`
2. 从 `custom` 新建测试分支，合并需要的上游 tag；不要强制覆盖。
3. 检查手柄输入、原生接口协议、DPI 档位兼容是否仍适用，运行单元测试。
4. 手动触发构建，下载 artifact 安装实测。
5. 验证后创建自己的 tag 和 Release，上传 APK，并明确标识定制版。

公开仓库不包含个人布局、日志、配对证书、SDK、构建缓存或签名密钥。首次整理版本的稳定 APK 与原生缩放已在实际平板验证可用；CI 后续构建不等同于设备实测。
