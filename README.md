# 党支部管理系统初稿

这是一个本地运行的 Spring Boot + Thymeleaf + SQLite 初稿。

## 当前功能

- 党员基础档案和发展阶段日期
- 预备党员、积极分子未来 90 天节点提醒
- 组织生活活动台账
- 会议记录
- 根据会议记录生成可编辑的 Word 纪要初稿
- 预留 `/api/members` 和 `/api/dashboard` 接口，后续可替换 Vue 前端

## 本地运行

环境要求：Java 21、Maven 3.9+。

```bash
mvn spring-boot:run
```

浏览器访问：<http://localhost:8080>

数据库文件会生成在 `data/party-management.db`。首次启动会自动创建表。

## 目录结构

```text
src/main/java/com/party/management/
  controller/   网页和预留 API
  model/        业务对象
  repository/   SQLite 数据访问
  service/      业务规则和 Word 生成
src/main/resources/
  templates/    Thymeleaf 页面
  static/css/   页面样式
  db/           数据库初始化脚本
data/           本地数据库和附件目录
```

提醒规则目前是演示版本，正式使用前需要按现行文件和支部实际要求校准。
