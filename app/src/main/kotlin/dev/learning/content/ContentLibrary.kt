package dev.learning.content

import dev.learning.Exercise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ModuleMeta(
    val moduleId: String,
    val difficulty: List<String>,
    val title: Map<String, String>,
    val description: Map<String, String>
)

@Serializable
data class UnitMeta(
    val unitId: String,
    val moduleId: String,
    val targetLanguage: String? = null,
    val title: Map<String, String>,
    val description: Map<String, String>,
    val tip: Map<String, String>? = null
)

@Serializable
data class UnitContent(
    val unitId: String,
    val moduleId: String,
    val targetLanguage: String? = null,
    val title: Map<String, String>,
    val description: Map<String, String>,
    val tip: Map<String, String>? = null,
    val exercises: List<Exercise>
)

open class ContentLibrary(private val basePath: String = "/content") {
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * Try to get a directory, first from classpath resources, then from filesystem
     */
    private fun getDirectory(path: String): File? {
        // First try classpath resources (for test environment)
        try {
            val resourcePath = if (path.startsWith("/")) path.removePrefix("/") else path
            val classLoader = this::class.java.classLoader
            val resourceUrl = classLoader.getResource(resourcePath)
            if (resourceUrl != null) {
                val file = File(resourceUrl.toURI())
                if (file.exists() && file.isDirectory) {
                    return file
                }
            }
        } catch (e: Exception) {
            // Ignore and try filesystem
        }
        
        // Fallback to filesystem
        val file = File(path)
        return if (file.exists() && file.isDirectory) file else null
    }
    
    /**
     * Get all modules for a specific target language
     * @param targetLanguage The target language code (e.g., "en", "es")
     * @return List of module metadata
     */
    open fun getModules(targetLanguage: String): List<ModuleMeta> {
        return try {
            val modulesPath = "$basePath/$targetLanguage/modules"
            val modulesDir = getDirectory(modulesPath)
            
            if (modulesDir == null) {
                println("Modules directory is not valid: $modulesPath")
                return emptyList()
            }
            
            val modules = mutableListOf<ModuleMeta>()
            
            modulesDir.listFiles { file -> file.isDirectory }?.forEach { moduleDir ->
                val metaFile = File(moduleDir, "meta.json")
                if (metaFile.exists()) {
                    try {
                        val metaContent = metaFile.readText()
                        val moduleMeta = json.decodeFromString<ModuleMeta>(metaContent)
                        modules.add(moduleMeta)
                    } catch (e: Exception) {
                        println("Error reading meta.json for module ${moduleDir.name}: ${e.message}")
                    }
                }
            }
            
            // Sort by module ID for consistent ordering
            modules.sortedBy { it.moduleId }
        } catch (e: Exception) {
            println("Error getting modules for language $targetLanguage: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get all units for a specific module (without exercises)
     * @param targetLanguage The target language code
     * @param moduleId The module identifier
     * @return List of unit metadata (without exercises)
     */
    open fun getUnits(targetLanguage: String, moduleId: String): List<UnitMeta> {
        return try {
            val modulePath = "$basePath/$targetLanguage/modules/$moduleId"
            val moduleDir = getDirectory(modulePath)
            
            if (moduleDir == null) {
                println("Module directory not found: $modulePath")
                return emptyList()
            }
            
            val unitsDir = File(moduleDir, "units")
            
            if (!unitsDir.exists() || !unitsDir.isDirectory) {
                println("Units directory not found: ${unitsDir.absolutePath}")
                return emptyList()
            }
            
            val units = mutableListOf<UnitMeta>()
            
            // Check for unit directories first
            unitsDir.listFiles { file -> file.isDirectory }?.forEach { unitDir ->
                val unitFile = File(unitDir, "unit.json")
                if (unitFile.exists()) {
                    try {
                        val unitContent = unitFile.readText()
                        val unitData = json.decodeFromString<UnitContent>(unitContent)
                        units.add(
                            UnitMeta(
                                unitId = unitData.unitId,
                                moduleId = unitData.moduleId,
                                targetLanguage = unitData.targetLanguage,
                                title = unitData.title,
                                description = unitData.description,
                                tip = unitData.tip
                            )
                        )
                    } catch (e: Exception) {
                        println("Error reading unit.json for unit ${unitDir.name}: ${e.message}")
                    }
                }
            }
            
            // Also check for JSON files directly in units directory (alternative structure)
            unitsDir.listFiles { file -> file.name.endsWith(".json") }?.forEach { unitFile ->
                try {
                    val unitContent = unitFile.readText()
                    val unitData = json.decodeFromString<UnitContent>(unitContent)
                    units.add(
                        UnitMeta(
                            unitId = unitData.unitId ?: unitFile.nameWithoutExtension,
                            moduleId = unitData.moduleId ?: moduleId,
                            targetLanguage = unitData.targetLanguage,
                            title = unitData.title,
                            description = unitData.description,
                            tip = unitData.tip
                        )
                    )
                } catch (e: Exception) {
                    println("Error reading unit file ${unitFile.name}: ${e.message}")
                }
            }
            
            // Sort by unit ID for consistent ordering
            units.sortedBy { it.unitId }
        } catch (e: Exception) {
            println("Error getting units for module $moduleId: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get all exercises for a specific unit
     * @param targetLanguage The target language code
     * @param moduleId The module identifier
     * @param unitId The unit identifier
     * @return List of exercises
     */
    fun getExercises(targetLanguage: String, moduleId: String, unitId: String): List<Exercise> {
        return try {
            val unitContent = getUnitContent(targetLanguage, moduleId, unitId)
            unitContent?.exercises ?: emptyList()
        } catch (e: Exception) {
            println("Error getting exercises for unit $unitId in module $moduleId: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get a specific exercise by ID
     * @param targetLanguage The target language code
     * @param moduleId The module identifier
     * @param unitId The unit identifier
     * @param exerciseId The exercise identifier
     * @return The exercise or null if not found
     */
    fun getExercise(targetLanguage: String, moduleId: String, unitId: String, exerciseId: String): Exercise? {
        return try {
            val exercises = getExercises(targetLanguage, moduleId, unitId)
            exercises.find { it.id == exerciseId }
        } catch (e: Exception) {
            println("Error getting exercise $exerciseId: ${e.message}")
            null
        }
    }
    
    /**
     * Get the complete unit content including exercises
     * @param targetLanguage The target language code
     * @param moduleId The module identifier
     * @param unitId The unit identifier
     * @return The complete unit content or null if not found
     */
    open fun getUnitContent(targetLanguage: String, moduleId: String, unitId: String): UnitContent? {
        return try {
            val modulePath = "$basePath/$targetLanguage/modules/$moduleId"
            val moduleDir = getDirectory(modulePath)
            
            if (moduleDir == null) {
                println("Module directory not found: $modulePath")
                return null
            }
            
            val unitsDir = File(moduleDir, "units")
            
            // Try unit directory structure first
            val unitDir = File(unitsDir, unitId)
            val unitFile = File(unitDir, "unit.json")
            
            if (unitFile.exists()) {
                val unitContent = unitFile.readText()
                return json.decodeFromString<UnitContent>(unitContent)
            }
            
            // Try direct JSON file in units directory
            val directUnitFile = File(unitsDir, "$unitId.json")
            if (directUnitFile.exists()) {
                val unitContent = directUnitFile.readText()
                val unitData = json.decodeFromString<UnitContent>(unitContent)
                return unitData.copy(
                    unitId = unitData.unitId ?: unitId,
                    moduleId = unitData.moduleId ?: moduleId
                )
            }
            
            println("Unit file not found for unit $unitId in module $moduleId")
            null
        } catch (e: Exception) {
            println("Error getting unit content for $unitId: ${e.message}")
            null
        }
    }
    
    /**
     * Get all exercises from all units in a module
     * @param targetLanguage The target language code
     * @param moduleId The module identifier
     * @return List of all exercises in the module
     */
    fun getAllExercisesInModule(targetLanguage: String, moduleId: String): List<Exercise> {
        return try {
            val units = getUnits(targetLanguage, moduleId)
            val allExercises = mutableListOf<Exercise>()
            
            units.forEach { unit ->
                val exercises = getExercises(targetLanguage, moduleId, unit.unitId)
                allExercises.addAll(exercises)
            }
            
            allExercises
        } catch (e: Exception) {
            println("Error getting all exercises for module $moduleId: ${e.message}")
            emptyList()
        }
    }
}
