# CrowdMesh proguard/R8 rules.
# Most androidx/Compose/Hilt/Room rules are supplied by consumer-rules.pro
# bundled inside those libraries' AARs; only project-specific exceptions live here.

# kotlinx.serialization keeps its own consumer rules, but the generated
# serializers for our wire model classes are looked up reflectively by name.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.crowdmesh.**$$serializer { *; }
-keepclassmembers class com.crowdmesh.** {
    *** Companion;
}
-keepclasseswithmembers class com.crowdmesh.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities/DAOs are referenced by generated code via reflection-free
# codegen, but keep model classes safe from aggressive shrinking regardless.
-keep class com.crowdmesh.data.local.entity.** { *; }
