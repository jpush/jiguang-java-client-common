# jiguang-java-client-common
Common lib for JiGuang Java clients. 

## 概述

这是极光 java client 的公共封装开发包，为 jpush, jmessage, jsms 等 client 提供公共依赖。

版本更新：[Release页面](https://github.com/jpush/jiguang-java-client-common/releases)。下载更新请到这里。

> 非常欢迎各位开发者提交代码，贡献一份力量，review过有效的代码将会合入本项目。


## 安装

### maven 方式
将下边的依赖条件放到你项目的 maven pom.xml 文件里。

```Java
<dependency>
    <groupId>cn.jpush.api</groupId>
    <artifactId>jiguang-common</artifactId>
    <version>0.1.1</version>
</dependency>
```
### jar 包方式

请到 [Release页面](https://github.com/jpush/jiguang-java-client-common/releases)下载相应版本的发布包。

### 依赖包
* [slf4j](http://www.slf4j.org/) / log4j (Logger)
* [gson](https://code.google.com/p/google-gson/) (Google JSON Utils)
* [jpush-client](https://github.com/jpush/jpush-api-java-client)

> 其中 slf4j 可以与 logback, log4j, commons-logging 等日志框架一起工作，可根据你的需要配置使用。

> jiguang-common的jar包下载。[请点击](https://github.com/jpush/jiguang-java-client-common/releases)

如果使用 Maven 构建项目，则需要在你的项目 pom.xml 里增加：

```Java
<dependency>
	<groupId>com.google.code.gson</groupId>
	<artifactId>gson</artifactId>
	<version>2.2.4</version>
</dependency>
<dependency>
	<groupId>org.slf4j</groupId>
	<artifactId>slf4j-api</artifactId>
	<version>1.7.5</version>
</dependency>
<dependency>
	<groupId>cn.jpush.api</groupId>
	<artifactId>jiguang-common</artifactId>
	<version>0.1.1</version>
</dependency>
<!-- For log4j -->
<dependency>
	<groupId>org.slf4j</groupId>
	<artifactId>slf4j-log4j12</artifactId>
	<version>1.7.5</version>
</dependency>
<dependency>
	<groupId>log4j</groupId>
	<artifactId>log4j</artifactId>
	<version>1.2.16</version>
</dependency>
```

如果不使用 Maven 构建项目，则项目 libs/ 目录下有依赖的 jar 可复制到你的项目里去。

## 编译源码

> 如果开发者想基于本项目做一些扩展的开发，或者想了解本项目源码，可以参考此章，否则可略过此章。
