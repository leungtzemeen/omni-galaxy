# CLAUDE.md - 后端开发指南与行为准则

This file provides strict guidance and behavioral rules for Claude Code (claude.ai/code) inside `backend/`.

---

## 1. 核心流程：实质性代码修改前置确认机制

- **触发条件**：涉及多文件修改、业务行为变更、配置语义调整、或非平凡重构前。
- **前置动作**：AI 必须**在执行前给出简短修改计划**，明确列出：
  1. 准备修改的文件路径与清单。
  2. 期望达到的核心业务目的。
  3. **明确写出“不打算触碰/改动”的边界与范围**。
- **执行原则**：在单次交互中以概要形式告知用户即可。若用户未提出异议或任务本身具有连续性，AI 可在同一轮对话中连贯执行，避免死板等待。
- **唯一例外**：若用户明确指出“列出计划等我确认”，或改动将超过整个模块架构边界，则必须绝对静默等待。

---

## 2. Java 后端开发与架构质量规范

- **设计思想**：坚持**面向接口编程**，严格遵循 **OCP (开闭原则)** 和 **SRP (单一职责原则)**，代码必须符合“高内聚、低耦合”的编程思想，确保系统易维护、易拓展。
- **设计模式**：**拒绝平铺直叙的代码**。面对复杂或多分支业务场景，必须合理运用设计模式（Strategy, Factory, Observer, Singleton 等）进行解耦。
- **规模限制**：严格控制文件体积。**单个 Java 类文件的总行数尽量控制在 500 行以内**。臃肿时 AI 必须主动提出类拆分方案。
- **Java 21 特性**：多利用 Java 21 虚拟线程特性，杜绝复杂的反射操作。方法重载或注入时确保形参名称明确，防止动态代理丢失参数名。
- **命名规范**：类/接口大驼峰 (`PascalCase`)，方法/变量小驼峰 (`lowerCamelCase`)，全局常量大写加下划线 (`UPPER_SNAKE_CASE`)。
- **单元测试特例**：测试方法名**允许并推荐使用下划线分段法**。格式统一为：`被测方法_测试场景_预期结果`（如 `methodName_scenario_expectedBehavior`）。严禁编写逻辑复杂的外部依赖夹具。
- **注释规约**：在编写或重构核心业务代码时，请秉持“核心代码有迹可循”的原则，在关键步骤、条件分支或防御设计上，慷慨地留下清晰的、解释“为什么这么做（Why）”而非仅仅“做了什么（What）”的行内注释或方法级注释或类级注释。这不仅能让代码更具可读性，也有助于我们后续的高效对齐。
---

## 3. 项目架构与 Maven 防污染红线

### 核心目录图谱
- `omni-galaxy-common/` — **【通用基础设施组件】**（纯工具，无业务倾向，如 `common-core`, `common-security`）
- `omni-galaxy-platform/` — **【核心数字底座平台】**（公共底座，如 `platform-gateway`, `platform-auth`, `platform-user`）
- `omni-galaxy-mall/` — **【电商业务域】**（包含独立运行的微服务进程，如 `mall-product-service`）
- `omni-galaxy-ai/` — **【AI 业务域】**（与电商平级，包含 `ai-chat-service`）

### 微服务双模块隔离与依赖准则（以商品微服务为例）
1. **`*-api` 模块 (对外二方包，打成 jar 包供外部依赖)**：仅定义分布式通信规约。包含 Feign 接口 (`feign/`)、传输对象 (`dto/`)、常量路由 (`constant/`)。**严格禁止编写任何涉及数据库、缓存等核心业务逻辑**。
2. **`*-biz` 模块 (对内核心业务实现，可运行进程)**：包含启动类、`controller/`、`service/impl/`、`mapper/`、`domain/Entity`。必须按需声明自身依赖，严禁在顶级 POM 中强制全员继承无关运行期组件。
3. **Maven 版本号红线**：子模块引入被顶级根 POM 的 `<dependencyManagement>` 仲裁过的依赖时，**必须绝对禁止编写 `<version>` 标签**。

---

## 4. OmniGalaxy 日志规约

所有后端 Java 代码中，打印日志必须遵循以下规范，严禁平铺直叙：
- **流量/请求切入**：`log.info(">>>> [模块名] 描述信息 context: {}", xxxx);`
- **流量/数据输出**：`log.info("<<<< [模块名] 描述信息 content: {}", xxxx);`
- **预期内业务警告**：`log.warn(">>>> [业务域] 异常警示信息（处理策略）: {}", xxxx);`
- **预期外严重故障**：`log.error(">>>> [核心底座] 严重崩溃描述", e);`
- **分层治理**：业务异常（如 `BizException`）和前端参数校验异常属于预期内逻辑，**严禁使用 `log.error`**。必须降级为 `log.warn` 或不打印，避免触发运维伪告警。只有核心底座崩溃、数据库断连等系统级故障才允许使用 `log.error`。

---

## 5. 数据库同步变更规约
- **数据库**：涉及数据库表结构的变动、新字段追加、或任何索引/约束（如 uk_type_identifier）的创建与修改，必须在操作 Java 实体的同时，同步修改以下路径下对应的 SQL 初始化或变更脚本。
- **脚本路径**：`D:\Projects\leungtzemeen\omni-galaxy\backend\sql\`
- **要求**：严禁出现 Java 实体与 SQL 文件状态脱节的情况，确保数据库基线文件与物理表结构、Mapper 状态实时严格对齐。
---

## 6. 常用开发命令集 (环境兼容: Git Bash)

```bash
# 清理并重新编译
mvn clean compile
# 打包项目（包含测试）
mvn clean package
# 打包项目（跳过测试）
mvn clean package -DskipTests
# 定向构建单模块 (以商品微服务为例)
mvn clean install -pl omni-galaxy-mall/mall-product-service -am -DskipTests
# 运行单元测试
mvn test -Dtest=YourTestClassName