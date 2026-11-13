// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

abstract class ExportRootApkTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val sourceApk: RegularFileProperty

  @get:OutputFile
  abstract val targetApk: RegularFileProperty

  @get:Internal
  abstract val legacyApk: RegularFileProperty

  @TaskAction
  fun copyApk() {
    val oldFile = legacyApk.orNull?.asFile
    if (oldFile != null && oldFile.exists()) {
      oldFile.delete()
    }
    val src = sourceApk.get().asFile
    val dst = targetApk.get().asFile
    dst.parentFile?.mkdirs()
    src.copyTo(dst, overwrite = true)
  }
}

tasks.register<ExportRootApkTask>("exportApkToProjectRoot") {
  dependsOn(":app:assembleDebug")
  sourceApk.set(layout.projectDirectory.file("app/build/outputs/apk/debug/app-debug.apk"))
  targetApk.set(layout.projectDirectory.file("app-release.apk"))
  legacyApk.set(layout.projectDirectory.file("Vyora.apk"))
}
