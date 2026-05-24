# CLAUDE.md - 后端开发指南与行为准则 (Backend Development Guide & Rules)

This file provides strict guidance and behavioral rules for Claude Code (claude.ai/code) when operating within the `backend/` directory of the `omni-galaxy` monorepo.

---

## 1. 核心流程：实质性代码修改前置确认机制 (Pre-change Plan Protocol)

- **触发条件 (Trigger)**：涉及多文件修改、业务行为变更、配置语义调整、或非平凡重构（Non-trivial Refactoring）等**实质性**代码改动前。
- **前置动作 (Required Action)**：AI 必须**在执行前给出简短的修改计划**，明确列出：
    1. 准备修改的文件路径与清单 (Affected paths & files)。
    2. 期望达到的核心业务目的 (Core purpose of the change)。
    3. **明确界定并写出“不打算触碰/改动”的边界与范围** (Explicit boundaries: what will NOT change)。
- **执行原则 (Execution Principle)**：计划给出后，**在单次交互中以概要形式告知用户即可。若用户未提出异议或任务本身具有连续性，AI 可在同一轮对话中连贯执行**，避免因死板等待中断自动化流水线。
- **唯一例外 (Strict Exception)**：若用户明确指出“不要擅自改动，列出计划等我确认”，或者改动范围将超过整个模块的架构边界时，则必须绝对静默等待用户指令。

---

## 2. Java 后端开发规范 (Backend Architecture & Coding Standards)

- **设计思想 (Design Principles)**：坚持**面向接口编程**。代码必须符合“高内聚、低耦合”的编程思想，严格遵循**开闭原则 (OCP)** 和**单一职责原则 (SRP)**，确保系统易维护、易拓展。
- **设计模式 (Design Patterns)**：**拒绝平铺直叙的代码**。在面对复杂或多分支的业务场景时，**必须合理运用符合当下语境的设计模式**（如策略模式 Strategy、工厂模式 Factory、观察者模式 Observer、单例模式 Singleton 等）进行优雅解耦。
- **规模限制 (File Size Limit)**：严格控制文件体积。**单个 Java 类文件的总行数尽量控制在 500 行以内**。当代码趋向臃肿时，AI 必须主动提出类拆分或逻辑提取方案。
- **命名规范 (Naming Conventions)**：
    * **类名/接口**：严格使用大驼峰 (`PascalCase`)。
    * **方法/变量**：严格遵循标准小驼峰命名 (`lowerCamelCase`)。
    * **全局常量**：必须使用大写加下划线 (`UPPER_SNAKE_CASE`)。
- **单元测试特例 (Unit Testing Exception)**：
    * 为保证测试报告和失败日志的可读性，测试方法名**允许并推荐使用下划线分段法**。
    * 命名格式统一为：`被测方法_测试场景_预期结果` (例如：`methodName_scenario_expectedBehavior`)。
    * 严禁在测试类中编写逻辑复杂的外部依赖夹具，优先使用断言库原生机制。

---

## 3. Maven 依赖边界与防污染机制（最高硬性红线）
- **严禁版本号冗余**：子模块在引入任何被顶级根 POM 的 `<dependencyManagement>` 仲裁过的依赖时（如 Spring Boot 全家桶、Spring Cloud 组件、Spring Cloud Alibaba、MyBatis-Plus、Lombok、MapStruct），**必须绝对禁止编写 `<version>` 标签**。版本必须无条件服从顶级根 POM 的法律约束。
- **严禁子模块依赖污染**：
  - 针对 `xxx-api` 模块（对外二方包）：只允许引入最基础的 `spring-cloud-starter-openfeign`。**严禁**引入 `spring-boot-starter-web`、MyBatis-Plus、MySQL驱动、Redis驱动等任何带有运行期数据库或重型组件的依赖。确保 API 包极度轻量、零污染。
  - 针对 `common-core` / `common-utils` 等基础组件：**严禁**引入带有特定具体业务倾向的依赖，保持纯工具属性。
- **单模块独立可跑**：任何独立启动运行的微服务进程（如 `xxx-biz` 模块），其所需的依赖必须在自身模块的 `pom.xml` 中清晰、精确地按需声明，严禁在顶级 POM 中强制全员继承无关运行期组件。

---

## 4. 常用开发命令集 (Build & Test Commands - Git Bash Env)

All commands must be compatible with the **Git Bash** environment.

```bash
# 清理并重新编译项目
mvn clean compile

# 打包项目（包含运行测试）
mvn clean package

# 打包项目（跳过测试）
mvn clean package -DskipTests

# 运行全部单元测试
mvn test

# 运行指定的单个测试类
mvn test -Dtest=YourTestClassName