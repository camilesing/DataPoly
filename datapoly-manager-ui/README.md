# datapoly-ui

## 一、介绍

基于Vue.js 2.0编写的datapoly管理web端。

## 二、环境

**node** : >= v14.15.4

### 1、CentOS下安装Nodejs

```
# 下载nodejs
wget https://nodejs.org/dist/v14.15.4/node-v14.15.4-linux-x64.tar.xz
# 解压缩
tar -xvf node-v14.15.4-linux-x64.tar.xz && mkdir -p /usr/local/nodejs && mv node-v14.15.4-linux-x64/* /usr/local/nodejs/
# 建立node软链接
ln -s /usr/local/nodejs/bin/node /usr/local/bin
# 建立npm 软链接
ln -s /usr/local/nodejs/bin/npm /usr/local/bin
# 设置国内淘宝镜像源
npm config set registry https://registry.npm.taobao.org
# 禁用ssl验证
npm config set strict-ssl false
# 查看设置信息
npm config list
# 验证是否安装成功
node -v
npm -v
```

### 2. Windows下安装Nodejs

从 [Node.js 官网](https://nodejs.org/dist/v14.15.4/) 下载 v14.15.4 安装包，按默认选项安装后执行 `node -v` / `npm -v` 验证。

## 三、构建

``` bash
# install dependencies
npm install

# serve with hot reload at localhost:8080
npm run dev

# build for production with minification
npm run build

# build for production and view the bundle analyzer report
npm run build --report
```

## 四、部署

执行`npm run build`后，`dist`目录会生成打包产物。产物需同步进后端资源目录后随 mvn 打包进入发行版：

- 推荐在仓库根目录执行 `sh ./build-ui.sh` —— 它用 `node:14-alpine` 容器完成 `npm install`、`npm run build`，并自动把 `dist/index.html` 与 `dist/static/` 同步（先清后拷）进 `datapoly-manager/src/main/resources/`，本机无需安装 Node。`build.sh` 与 `docker-maven-build.sh` 打包前已内置该步骤。
- 如本机已装 Node 14，也可手工执行 `npm run build`，再把 `dist` 目录下的 `index.html` 与 `static/` 拷贝（替换）到 `datapoly-manager/src/main/resources/`，然后对整个 datapoly 项目执行 mvn 打包。

上述同步进 `datapoly-manager/src/main/resources/` 的文件（`index.html`、`static/`）为构建产物，不提交 git（已在仓库 .gitignore 排除）。
