# privacy-demo

这个示例展示 `spring-privacy-guard` 如何完成三条链路：接口字段脱敏、日志脱敏、审计事件。

## 使用方式

1. 先在工作区根目录执行：
   - `mvnw.cmd -DskipTests install`
2. 进入当前目录后启动应用：
   - `mvnw.cmd spring-boot:run`
3. 访问接口：
   - `http://localhost:8088/patients/demo`
4. 查看内存审计事件：
   - `http://localhost:8088/audit-events`

## 说明

- 当前 sample 使用：`privacy.guard.audit.repository-type=IN_MEMORY`
- 如果切换到 JDBC，需要额外提供数据源和审计表结构