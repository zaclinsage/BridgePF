name := "BridgePF"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Heroku Maven repository" at "http://s3pository.heroku.com/maven-central/"

resolvers += "Bridge Maven repository" at "https://repo-maven.sagebridge.org/"

resolvers += "Syanpse repository" at "http://sagebionetworks.artifactoryonline.com/sagebionetworks/libs-releases-local/"

resolvers += Resolver.mavenLocal

libraryDependencies ++= Seq(
  cache,
  filters,
  // Sage packages
  "org.sagebionetworks" % "bridge-base" % "2.7.4",
  "org.sagebionetworks" % "synapseJavaClient" % "161.0-4-g843de2c",
  // AWS
  "com.amazonaws" % "aws-java-sdk-s3" % "1.10.20",
  "com.amazonaws" % "aws-java-sdk-sqs" % "1.10.20",
  "com.amazonaws" % "aws-java-sdk-sts" % "1.10.20",
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.10.20",
  "com.amazonaws" % "aws-java-sdk-ses" % "1.10.20",
  
  // New Relic
  "com.newrelic.agent.java" % "newrelic-agent" % "3.32.0",
  // Dom4j, needed to resolve dependency conflicts for Hibernate
  "dom4j" % "dom4j" % "1.6.1",
  // MySQL JDBC connector
  "mysql" % "mysql-connector-java" % "5.1.41",
  // Hibernate (SQL ORM)
  "org.hibernate" % "hibernate-core" % "5.2.9.Final",
  // C3P0, used for connection pooling for Hibernate
 "org.hibernate" % "hibernate-c3p0" % "5.2.9.Final",
  // Spring
  "org.springframework" % "spring-context" % "4.3.7.RELEASE",
  // Apache Commons
  "org.apache.commons" % "commons-lang3" % "3.4",
  "commons-validator" % "commons-validator" % "1.4.1",
  "commons-io" % "commons-io" % "2.4",
  "org.apache.logging.log4j" % "log4j-api" % "2.6.1",
  // Jackson
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.7.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.7.3",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.7.3",
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % "2.7.3",
  // Guava
  "com.google.guava" % "guava" % "18.0",
  // Guice
  "com.google.inject" % "guice" % "4.0",
  // Quartz
  "org.quartz-scheduler" % "quartz" % "2.2.1",
  // Mail
  "javax.mail" % "mail" % "1.4.7",
  // Joda-Time
  "joda-time" % "joda-time" % "2.8.2",
  // Stormpath
  "com.stormpath.sdk" % "stormpath-sdk-api" % "1.0.RC9.2",
  "com.stormpath.sdk" % "stormpath-sdk-httpclient" % "1.0.RC9.2",
  "org.apache.httpcomponents" % "httpclient" % "4.5",
  // Redis
  "redis.clients" % "jedis" % "2.7.2",
  // PDF, HTML
  "org.xhtmlrenderer" % "flying-saucer-pdf" % "9.0.7",
  "org.jsoup" % "jsoup" % "1.8.3",

  // Test
  javaWs % Test,
  "junit" % "junit" % "4.12" % Test,
  "org.mockito" % "mockito-core" % "1.10.19" % Test,
  "org.sagebionetworks" % "BridgeTestUtils" % "1.1" % Test,
  "org.springframework" % "spring-test" % "4.2.4.RELEASE" % Test,
  "nl.jqno.equalsverifier" % "equalsverifier" % "1.7.2" % Test
)

lazy val root = (project in file(".")).enablePlugins(PlayJava)

routesGenerator := InjectedRoutesGenerator

testOptions += Tests.Argument(TestFrameworks.JUnit, "-a")

// Compile before generating eclipse files
EclipseKeys.preTasks := Seq(compile in Compile)
// Java project files only
EclipseKeys.projectFlavor := EclipseProjectFlavor.Java
EclipseKeys.createSrc := EclipseCreateSrc.ValueSet(EclipseCreateSrc.ManagedClasses, EclipseCreateSrc.ManagedResources)  // Use .class files instead 

