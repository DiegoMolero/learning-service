package dev.learning.content

import kotlin.test.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ContentLibraryTest {
    
    private lateinit var contentLibrary: ContentLibrary
    
    @BeforeEach
    fun setUp() {
        // Use ContentLibrary with test content path  
        contentLibrary = ContentLibrary()
    }

    @Test
    fun `test getModules returns list of modules for valid language`() {
        val modules = contentLibrary.getModules("en")
        
        assertNotNull(modules)
        assertTrue(modules.isNotEmpty(), "Should have at least one module")
        
        // Check for articles-determiners module
        val articlesModule = modules.find { it.moduleId == "articles-determiners" }
        assertNotNull(articlesModule, "Should find articles-determiners module")
        assertEquals("articles-determiners", articlesModule!!.moduleId)
        assertNotNull(articlesModule.title)
        assertTrue(articlesModule.title.containsKey("en"))
        assertTrue(articlesModule.title.containsKey("es"))
        assertEquals("Articles & Determiners", articlesModule.title["en"])
        assertEquals("Artículos y Determinantes", articlesModule.title["es"])
    }

    @Test
    fun `test getModules returns empty list for invalid language`() {
        val modules = contentLibrary.getModules("invalid")
        
        assertNotNull(modules)
        assertTrue(modules.isEmpty())
    }

    @Test
    fun `test getUnits returns list of units for valid module`() {
        val units = contentLibrary.getUnits("en", "articles-determiners")
        
        assertNotNull(units)
        assertTrue(units.isNotEmpty())
        
        // Check for the unit we know exists
        val theArticleUnit = units.find { it.unitId == "the_article_general_vs_specific_1" }
        assertNotNull(theArticleUnit)
        assertEquals("the_article_general_vs_specific_1", theArticleUnit!!.unitId)
        assertEquals("articles-determiners", theArticleUnit.moduleId)
        assertNotNull(theArticleUnit.title)
        assertTrue(theArticleUnit.title.containsKey("en"))
        assertTrue(theArticleUnit.title.containsKey("es"))
        assertTrue(theArticleUnit.title["en"]!!.contains("The Article 'The'"))
    }

    @Test
    fun `test getUnits returns empty list for invalid module`() {
        val units = contentLibrary.getUnits("en", "invalid-module")
        
        assertNotNull(units)
        assertTrue(units.isEmpty())
    }

    @Test
    fun `test getUnitContent returns content for valid unit`() {
        val unitContent = contentLibrary.getUnitContent("en", "articles-determiners", "the_article_general_vs_specific_1")
        
        assertNotNull(unitContent)
        assertEquals("the_article_general_vs_specific_1", unitContent!!.unitId)
        assertEquals("articles-determiners", unitContent.moduleId)
        assertNotNull(unitContent.title)
        assertNotNull(unitContent.exercises)
        assertTrue(unitContent.exercises.isNotEmpty())
        
        // Verify exercises structure
        val firstExercise = unitContent.exercises[0]
        assertEquals("ex_1", firstExercise.id)
        assertEquals("translation", firstExercise.type)
        assertNotNull(firstExercise.prompt)
        assertNotNull(firstExercise.solution)
        assertEquals("Men don't understand women.", firstExercise.solution)
    }

    @Test
    fun `test getUnitContent returns null for invalid unit`() {
        val unitContent = contentLibrary.getUnitContent("en", "articles-determiners", "invalid-unit")
        
        assertNull(unitContent)
    }

    @Test
    fun `test getExercises returns list of exercises for valid unit`() {
        val exercises = contentLibrary.getExercises("en", "articles-determiners", "the_article_general_vs_specific_1")
        
        assertNotNull(exercises)
        assertTrue(exercises.isNotEmpty())
        assertTrue(exercises.size >= 40) // Should have many exercises
        
        // Check first exercise
        val exercise1 = exercises[0]
        assertEquals("ex_1", exercise1.id)
        assertEquals("translation", exercise1.type)
        assertEquals("Los hombres no entienden a las mujeres.", exercise1.prompt.es)
        assertEquals("Men don't understand women.", exercise1.solution)
        
        // Verify second exercise  
        val exercise2 = exercises[1]
        assertEquals("ex_2", exercise2.id)
        assertEquals("translation", exercise2.type)
        assertEquals("En general, a los españoles les gusta ir a la playa.", exercise2.prompt.es)
        assertEquals("In general, Spaniards like going to the beach.", exercise2.solution)
    }
    
    @Test
    fun `test getExercises returns empty list for invalid unit`() {
        val exercises = contentLibrary.getExercises("en", "articles-determiners", "invalid-unit")
        
        assertNotNull(exercises)
        assertTrue(exercises.isEmpty())
    }
    
    @Test
    fun `test getExercise returns specific exercise when found`() {
        val exercise = contentLibrary.getExercise("en", "articles-determiners", "the_article_general_vs_specific_1", "ex_1")
        
        assertNotNull(exercise)
        assertEquals("ex_1", exercise!!.id)
        assertEquals("translation", exercise.type)
        assertEquals("Los hombres no entienden a las mujeres.", exercise.prompt.es)
        assertEquals("Men don't understand women.", exercise.solution)
        assertNotNull(exercise.tip)
        assertTrue(exercise.tip!!.containsKey("en"))
        assertTrue(exercise.tip!!.containsKey("es"))
    }
    
    @Test
    fun `test getExercise returns null when not found`() {
        val exercise = contentLibrary.getExercise("en", "articles-determiners", "the_article_general_vs_specific_1", "invalid-exercise")
        
        assertNull(exercise)
    }

    @Test
    fun `test module data integrity`() {
        val modules = contentLibrary.getModules("en")
        
        for (module in modules) {
            // Every module should have required fields
            assertNotNull(module.moduleId)
            assertTrue(module.moduleId.isNotEmpty())
            assertNotNull(module.title)
            assertTrue(module.title.isNotEmpty())
            assertNotNull(module.description)
            assertTrue(module.description.isNotEmpty())
            assertNotNull(module.difficulty)
            assertTrue(module.difficulty.isNotEmpty())
            
            // Check multilingual support
            assertTrue(module.title.containsKey("en"))
            assertTrue(module.title.containsKey("es"))
            assertTrue(module.description.containsKey("en"))
            assertTrue(module.description.containsKey("es"))
        }
    }

    @Test
    fun `test unit data integrity`() {
        val modules = contentLibrary.getModules("en")
        
        for (module in modules) {
            val units = contentLibrary.getUnits("en", module.moduleId)
            
            for (unit in units) {
                // Every unit should have required fields
                assertNotNull(unit.unitId)
                assertTrue(unit.unitId.isNotEmpty())
                assertNotNull(unit.title)
                assertTrue(unit.title.isNotEmpty())
                assertNotNull(unit.description)
                assertTrue(unit.description.isNotEmpty())
                
                // Check multilingual support
                assertTrue(unit.title.containsKey("en"))
                assertTrue(unit.title.containsKey("es"))
                assertTrue(unit.description.containsKey("en"))
                assertTrue(unit.description.containsKey("es"))
            }
        }
    }

    @Test
    fun `test exercise data integrity`() {
        val modules = contentLibrary.getModules("en")
        
        for (module in modules) {
            val units = contentLibrary.getUnits("en", module.moduleId)
            
            for (unit in units) {
                val exercises = contentLibrary.getExercises("en", module.moduleId, unit.unitId)
                
                for (exercise in exercises) {
                    // Every exercise should have required fields
                    assertNotNull(exercise.id)
                    assertTrue(exercise.id.isNotEmpty())
                    assertNotNull(exercise.type)
                    assertTrue(exercise.type.isNotEmpty())
                    assertNotNull(exercise.prompt)
                    assertNotNull(exercise.solution)
                    assertTrue(exercise.solution.isNotEmpty())
                    
                    // Verify valid exercise types
                    assertTrue(exercise.type in listOf("multiple-choice", "translation", "fill-in-the-blank"))
                    
                    // Verify multilingual prompts - at least one language should be present
                    assertTrue(exercise.prompt.en != null || exercise.prompt.es != null)
                }
            }
        }
    }

    @Test
    fun `test error handling for invalid paths`() {
        // These should not throw exceptions but return empty/null results
        val invalidModules = contentLibrary.getModules("nonexistent")
        assertTrue(invalidModules.isEmpty())
        
        val invalidUnits = contentLibrary.getUnits("en", "nonexistent-module")
        assertTrue(invalidUnits.isEmpty())
        
        val invalidUnitContent = contentLibrary.getUnitContent("en", "articles-determiners", "nonexistent-unit")
        assertNull(invalidUnitContent)
        
        val invalidExercises = contentLibrary.getExercises("en", "nonexistent", "nonexistent")
        assertTrue(invalidExercises.isEmpty())
        
        val invalidExercise = contentLibrary.getExercise("en", "articles-determiners", "the_article_general_vs_specific_1", "nonexistent")
        assertNull(invalidExercise)
    }

    @Test
    fun `test specific test data content verification`() {
        // Verify the specific test data we created
        val modules = contentLibrary.getModules("en")
        assertTrue(modules.isNotEmpty(), "Should have test modules")
        
        val articlesModule = modules.find { it.moduleId == "articles-determiners" }
        assertNotNull(articlesModule)
        assertEquals("Articles & Determiners", articlesModule!!.title["en"])
        assertEquals("Artículos y Determinantes", articlesModule.title["es"])
        assertEquals(listOf("A2", "B1"), articlesModule.difficulty)
    }

    @Test
    fun `test exercise options and solutions`() {
        val exercise = contentLibrary.getExercise("en", "articles-determiners", "the_article_general_vs_specific_1", "ex_1")
        
        assertNotNull(exercise)
        assertEquals("translation", exercise!!.type)
        assertEquals("Los hombres no entienden a las mujeres.", exercise.prompt.es)
        assertEquals("Men don't understand women.", exercise.solution)
        // Translation exercises don't need options
        assertTrue(exercise.options == null || exercise.options!!.isEmpty())
    }
}
