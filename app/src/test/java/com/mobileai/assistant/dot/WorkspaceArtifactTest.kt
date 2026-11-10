package com.mobileai.assistant.dot

import com.mobileai.assistant.data.artifact.ArtifactRepositoryImpl
import com.mobileai.assistant.domain.artifact.ArtifactType
import com.mobileai.assistant.domain.artifact.WorkspaceArtifact
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceArtifactTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var rootDir: File
    private lateinit var repository: ArtifactRepositoryImpl

    @Before
    fun setUp() {
        rootDir = tempFolder.newFolder("artifact_repo_test")
        repository = ArtifactRepositoryImpl(rootDir)
    }

    @Test
    fun testArtifactCreationAndInitialVersion() = runBlocking {
        val artifact = WorkspaceArtifact(
            id = "art-001",
            dotId = "builtin-coder",
            conversationId = "conv-101",
            taskId = "task-500",
            title = "DatabaseHelper.kt",
            type = ArtifactType.CODE,
            language = "kotlin",
            content = "class DatabaseHelper { /* v1 */ }",
            version = 1
        )

        val saveRes = repository.saveArtifact(artifact)
        assertTrue(saveRes.isSuccess)

        val fetched = repository.getArtifactById("art-001")
        assertNotNull(fetched)
        assertEquals("DatabaseHelper.kt", fetched?.title)
        assertEquals(ArtifactType.CODE, fetched?.type)
        assertEquals("kotlin", fetched?.language)
        assertEquals(1, fetched?.version)

        val versions = repository.getArtifactVersionsList("art-001")
        assertEquals(1, versions.size)
        assertEquals(1, versions[0].version)
        assertEquals("class DatabaseHelper { /* v1 */ }", versions[0].content)
        assertEquals("Initial creation", versions[0].changeSummary)
    }

    @Test
    fun testArtifactVersioningAndRevisionHistory() = runBlocking {
        val artifact = WorkspaceArtifact(
            id = "art-arch-doc",
            dotId = "builtin-writer",
            conversationId = "conv-202",
            title = "Architecture.md",
            type = ArtifactType.MARKDOWN,
            language = "markdown",
            content = "# Architecture\nInitial draft."
        )
        repository.saveArtifact(artifact)

        // Revision 2
        val updateRes2 = repository.updateArtifact(
            id = "art-arch-doc",
            content = "# Architecture\nUpdated with diagram.",
            changeSummary = "Added diagram section"
        )
        assertTrue(updateRes2.isSuccess)
        val updatedArt2 = updateRes2.getOrNull()
        assertNotNull(updatedArt2)
        assertEquals(2, updatedArt2?.version)
        assertEquals("# Architecture\nUpdated with diagram.", updatedArt2?.content)

        // Revision 3
        val updateRes3 = repository.updateArtifact(
            id = "art-arch-doc",
            content = "# Architecture\nFinal reviewed version.",
            changeSummary = "Final edits"
        )
        assertTrue(updateRes3.isSuccess)
        val updatedArt3 = updateRes3.getOrNull()
        assertEquals(3, updatedArt3?.version)

        // Verify version history has all 3 versions preserved
        val allVersions = repository.getArtifactVersionsList("art-arch-doc")
        assertEquals(3, allVersions.size)

        assertEquals(1, allVersions[0].version)
        assertEquals("# Architecture\nInitial draft.", allVersions[0].content)
        assertEquals("Initial creation", allVersions[0].changeSummary)

        assertEquals(2, allVersions[1].version)
        assertEquals("# Architecture\nUpdated with diagram.", allVersions[1].content)
        assertEquals("Added diagram section", allVersions[1].changeSummary)

        assertEquals(3, allVersions[2].version)
        assertEquals("# Architecture\nFinal reviewed version.", allVersions[2].content)
        assertEquals("Final edits", allVersions[2].changeSummary)
    }

    @Test
    fun testArtifactIsolationByDotAndConversation() = runBlocking {
        val coderArt = WorkspaceArtifact(
            id = "art-coder",
            dotId = "builtin-coder",
            conversationId = "conv-coder-1",
            title = "Algorithm.kt",
            type = ArtifactType.CODE,
            content = "fun solve() = 42"
        )
        val writerArt = WorkspaceArtifact(
            id = "art-writer",
            dotId = "builtin-writer",
            conversationId = "conv-writer-1",
            title = "Report.md",
            type = ArtifactType.MARKDOWN,
            content = "# Report"
        )

        repository.saveArtifact(coderArt)
        repository.saveArtifact(writerArt)

        val coderArtifacts = repository.getArtifactsForDot("builtin-coder").first()
        val writerArtifacts = repository.getArtifactsForDot("builtin-writer").first()

        assertEquals(1, coderArtifacts.size)
        assertEquals("art-coder", coderArtifacts.first().id)

        assertEquals(1, writerArtifacts.size)
        assertEquals("art-writer", writerArtifacts.first().id)

        val convCoderArtifacts = repository.getArtifactsForConversation("conv-coder-1").first()
        assertEquals(1, convCoderArtifacts.size)
        assertEquals("art-coder", convCoderArtifacts.first().id)
    }

    @Test
    fun testDeleteArtifactCascadesToVersions() = runBlocking {
        val artifact = WorkspaceArtifact(
            id = "art-to-delete",
            dotId = "builtin-researcher",
            conversationId = "conv-res",
            title = "Dataset.csv",
            type = ArtifactType.CSV,
            content = "a,b,c\n1,2,3"
        )
        repository.saveArtifact(artifact)
        repository.updateArtifact("art-to-delete", "a,b,c\n1,2,3\n4,5,6", "Added row 2")

        assertEquals(2, repository.getArtifactVersionsList("art-to-delete").size)

        val deleteRes = repository.deleteArtifact("art-to-delete")
        assertTrue(deleteRes.isSuccess)

        assertNull(repository.getArtifactById("art-to-delete"))
        assertEquals(0, repository.getArtifactVersionsList("art-to-delete").size)
    }

    @Test
    fun testArtifactPersistenceOnDisk() = runBlocking {
        val artifact = WorkspaceArtifact(
            id = "persisted-art",
            dotId = "builtin-coder",
            conversationId = "conv-main",
            title = "Schema.json",
            type = ArtifactType.JSON,
            content = "{\"status\": \"ok\"}"
        )
        repository.saveArtifact(artifact)

        val file = File(rootDir, ArtifactRepositoryImpl.FILE_ARTIFACTS)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)

        // Reload in brand new repository instance
        val newRepo = ArtifactRepositoryImpl(rootDir)
        val loaded = newRepo.getArtifactById("persisted-art")
        assertNotNull(loaded)
        assertEquals("Schema.json", loaded?.title)
        assertEquals(ArtifactType.JSON, loaded?.type)

        val versions = newRepo.getArtifactVersionsList("persisted-art")
        assertEquals(1, versions.size)
        assertEquals("{\"status\": \"ok\"}", versions.first().content)
    }
}
