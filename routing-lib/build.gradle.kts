plugins {
    `cpp-library`
}

library {
    targetMachines.add(machines.linux.x86_64)
    linkage = listOf(Linkage.SHARED)
}

tasks.withType<CppCompile>().configureEach {
    val jvmHome = org.gradle.internal.jvm.Jvm.current().javaHome

    compilerArgs.addAll(
        listOf(
            "-I",
            "${jvmHome}/include",
            "-I",
            "${jvmHome}/include/linux",
            "-D_FILE_OFFSET_BITS=64"
        )
    )
}
tasks.withType<LinkSharedLibrary>().configureEach {
    linkerArgs.addAll(
        listOf(
            "-L",
            "/home/maxl/Downloads/pgrouting/build/lib/",
            "-l",
            "pgrouting-3.4",
            "-l",
            "sqlite3"
        )
    )
}

