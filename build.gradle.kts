plugins {
    kotlin("jvm") version "2.4.0" apply false
}

// Indicate DCEVM support if the JVM properties match a JVM known to support it.
ext.set("jvmSupportsDCEVM",
    System.getProperty("java.vendor", "goog corporation") == "JetBrains s.r.o."
)
