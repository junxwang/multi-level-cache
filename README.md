# Multi-Level Cache Framework

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
![Java Version](https://img.shields.io/badge/java-17%2B-orange)
![Spring Boot](https://img.shields.io/badge/spring--boot-3.4.4-green)

基于 Spring Boot 的多级缓存框架，支持本地缓存（Caffeine）和分布式缓存（Redis）的组合使用，有效提升系统性能并解决缓存穿透、击穿、雪崩等问题。

## 🌟 特性

- **两级缓存架构**：本地 Caffeine 缓存（L1）+ Redis 分布式缓存（L2）
- **自动配置**：基于 Spring Boot Auto-Configuration 实现开箱即用
- **缓存一致性**：通过 Redis Pub/Sub 实现分布式环境下缓存失效同步
- **类型安全**：自动类型恢复，避免反序列化为 LinkedHashMap
- **双模式支持**：同时支持 Reactive Redis 和普通 Redis
- **自动降级**：L1 未命中时自动查询 L2，L2 未命中时查询数据源
- **易于集成**：兼容 Spring Cache 抽象，无缝对接现有项目

## 📦 依赖引入

### Maven

```xml
<dependency>
    <groupId>io.github.wangjx</groupId>
    <artifactId>multi-level-cache-spring-boot-starter</artifactId>
    <version>${latest.version}</version>
</dependency>
```

### Redis 依赖（二选一）

**方式一：使用 Reactive Redis（推荐，性能更好）**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

**方式二：使用普通 Redis**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

> 注意：框架会根据类路径中的依赖自动选择对应的实现方式。如果两种依赖都存在，优先使用 Reactive Redis。

## ⚙️ 基础配置

```properties
# 启用 Spring Cache（交由 Starter 管理 CacheManager）
spring.cache.type=NONE

# 多级缓存配置
cache.multilevel.local-cache-max-size=2000
cache.multilevel.local-cache-expire-seconds=300
cache.multilevel.redis-cache-expire-seconds=3600
cache.multilevel.redis-timeout=5
# 自定义缓存失效广播频道（可选）
cache.multilevel.invalidation-channel=cache:invalidation:custom
```

### 参数说明

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `cache.multilevel.local-cache-max-size` | 1000 | 本地 Caffeine 最大缓存条数 |
| `cache.multilevel.local-cache-expire-seconds` | 300 | 本地缓存写入过期秒数 |
| `cache.multilevel.redis-cache-expire-seconds` | 3600 | Redis 二级缓存写入过期秒数 |
| `cache.multilevel.redis-timeout` | 3 | 与 Redis 交互的超时秒数 |
| `cache.multilevel.invalidation-channel` | `cache:invalidation` | 缓存失效广播的 Redis Pub/Sub 频道名称 |

## 🚀 使用方式

```java
@Service
public class DemoService {

    @Cacheable(cacheNames = "user", key = "#id")
    public User findUser(Long id) {
        // 查询数据库
        return userRepository.findById(id);
    }
    
    @CacheEvict(cacheNames = "user", key = "#id")
    public void updateUser(Long id, User user) {
        // 更新用户，自动清除缓存
        userRepository.save(user);
    }
    
    @CacheEvict(cacheNames = "user", allEntries = true)
    public void clearUserCache() {
        // 清除所有用户缓存
    }
}
```

## 🧠 多级缓存架构

```
┌─────────────────┐    Miss    ┌──────────────────┐    Miss    ┌────────────────┐
│   Application   ├───────────▶│  L1: Caffeine    ├───────────▶│  L2: Redis     │
└─────────────────┘            └──────────────────┘            └────────────────┘
     ▲                                 │                                │
     │ Hit                             │ Hit                            │ Hit
     └─────────────────────────────────┴────────────────────────────────┘
```

### 缓存失效机制

- **本地失效**：写入/删除缓存时自动清除本地 L1 缓存
- **分布式失效**：通过 Redis Pub/Sub 广播失效消息，所有实例同步清除本地缓存
- **自定义频道**：支持配置自定义的 Pub/Sub 频道名称

## 🔧 调试日志

启用调试日志可以帮助您更好地理解缓存的工作过程：

```properties
# 启用所有多级缓存相关组件的 DEBUG 日志
logging.level.io.github.wangjx.multilevelcache=DEBUG
```

## 📦 本地编译打包

### 环境要求

- Java 17 或更高版本
- Maven 3.6.0 或更高版本
- 网络连接（用于下载依赖）

### 编译项目

```bash
# 克隆项目到本地
git clone https://github.com/wangjx/multi-level-cache.git

# 进入项目目录
cd multi-level-cache

# 编译项目
mvn clean compile
```

### 运行测试

```bash
# 运行单元测试和集成测试
mvn test

# 运行测试并生成覆盖率报告
mvn verify
```

### 打包项目

```bash
# 打包项目（包含编译、测试、打包）
mvn clean package

# 跳过测试打包（不推荐）
mvn clean package -DskipTests

# 生成可执行 jar 包（如果有主类）
mvn clean package spring-boot:repackage
```

### 安装到本地仓库

```bash
# 安装到本地 Maven 仓库
mvn clean install

# 跳过测试安装
mvn clean install -DskipTests
```
### 常见问题

1. **依赖下载失败**
   ```bash
   # 强制更新依赖
   mvn clean compile -U
   ```

2. **内存不足**
   ```bash
   # 增加 Maven 内存
   export MAVEN_OPTS="-Xmx2048m"
   mvn clean package
   ```

3. **查看详细构建信息**
   ```bash
   # 显示详细输出
   mvn clean package -X
   ```

以上命令可以在项目的根目录下运行，构建成功后会在 `target/` 目录下生成相应的 jar 包。

## 🛠️ 自动装配验证

如果引入依赖后自动装配未生效，请按以下步骤排查：

1. 检查是否正确引入了 Redis 相关依赖
2. 确认 Redis 配置正确（host、port 等）
3. 查看日志中是否有相关 Bean 被创建：
   - `MultiLevelCacheManager`
   - `CacheInvalidationService`
   - `LockManager`

## 📄 许可证

本项目采用 Apache License 2.0 许可证，详见 [LICENSE](LICENSE) 文件。