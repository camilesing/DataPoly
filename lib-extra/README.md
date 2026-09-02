# lib-extra — 宿主扩展运行投放目录

发行版装配（`datapoly-dist/src/main/assembly/package.xml`）的宿主投放点：构建发行版时，
本目录下所有 `*.jar` 被打进 `lib/common/`，进而进入 manager / executor / gateway 的运行时
classpath（三服务的 `bin/datapolyctl.sh` 均以 `lib/common/*` 通配加载）。

宿主本地扩展模块（根目录 `datapoly-extension-*`，已被 `.gitignore` 排除）构建后，
用 `dev-local/dev.sh` 一键把产物同步到这里（第三方额外 jar 也可手工投放）。

- 仅 `*.jar` 参与装配；本文件与 `.gitkeep` 只占位，保证目录在干净 clone 中恒存在。
- 手工清理（回归纯开源构建）：`rm -f lib-extra/*.jar`
- 清空后重新 `mvn clean package`（或 `./build.sh`）即为不含投放物的纯开源发行版。