# Snipaste-Android

电脑上常用的截图、贴图工具，苦于没有安卓版本，遂自学了一些基础的安卓开发编写了一个。(非`snipaste`官方)

## 功能
目前有贴文字、贴图片的功能。

1、贴文字

- 输入文字生成悬浮窗

- 剪贴板文字生成悬浮窗

​2、贴图片

- 相册图片生成悬浮窗

- 拍照图片生成悬浮窗

- 生成悬浮窗前裁剪图片

3、透明度调整功能

4、双击关闭悬浮窗

5、支持多个悬浮窗

## 截图
<img src="screenshot/1.jpg" width="300"/>
<img src="screenshot/2.jpg" width="300"/>
<img src="screenshot/3.jpg" width="300"/>
<img src="screenshot/4.jpg" width="300"/>

## 下载
已改名上架酷安：[点击这里](https://www.coolapk.com/apk/255872)


## todo
- [ ] 区域截图
- [ ] 贴视频
- [ ] 其他视频软件投屏到悬浮窗上，以实现不带小窗功能的软件能够使用小窗播放功能。

## 依赖
- [QMUI](https://github.com/Tencent/QMUI_Android)，界面使用的是这个
- [Easy_Float](https://github.com/princekin-f/EasyFloat)，悬浮窗的核心
- [fluid-slider](https://github.com/Ramotion/fluid-slider-android) 底部的液态滑块

## 构建

项目已配置 GitHub Actions 自动构建，仓库下推送代码即可在 Actions 页面下载 Debug APK。

- 推送任意分支:触发构建 Debug + Release APK,产物可在 Actions → Artifacts 下载
- 推送 `v*` 标签(如 `v1.0.0`):自动发布 Release APK 到 GitHub Releases

### 自定义 Release 签名(可选)

如需签名自己的 Release APK,在 GitHub 仓库 `Settings → Secrets and variables → Actions` 添加以下 secret:

| Secret 名 | 内容 | 生成方式 |
|---|---|---|
| `KEYSTORE_BASE64` | keystore 文件的 Base64 编码 | `base64 snipaste.keystore \| tr -d '\n'` |

工作流会自动解码并存为 `app/snipaste.keystore`,再通过 `app/build.gradle` 中的 `signingConfigs` 引用。