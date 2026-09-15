# lib-extra — 宿主扩展运行投放目录

发行版装配（`datapoly-dist/src/main/assembly/package.xml`）的宿主投放点：构建发行版时，
本目录下所有 `*.jar` 被打进 `lib/common/`，进而进入 manager / executor / gateway 的运行时
classpath（三服务的 `bin/datapolyctl.sh` 均以 `lib/common/*` 通配加载）。

宿主扩展（根目录 `datapoly-extension/`，已被 `.gitignore` 排除，独立 git 仓库）构建后的产物由入库脚本
`build-extension.sh` 自动投放到这里——`build.sh` / `docker-maven-build.sh` 会先调用它（无环境变量配置时
该脚本为无操作）。本地一键入口仍是 `dev-local/dev.sh`（薄包装：注入 env.sh 后转发）。第三方额外 jar 也可手工投放。

- 仅 `*.jar` 参与装配；本文件与 `.gitkeep` 只占位，保证目录在干净 clone 中恒存在。
- 手工清理（回归纯开源构建）：`rm -f lib-extra/*.jar`
- 清空后重新 `mvn clean package`（或 `./build.sh`）即为不含投放物的纯开源发行版。