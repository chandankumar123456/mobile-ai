package com.mobileai.assistant.data.a2ui

import com.mobileai.assistant.domain.a2ui.A2UiMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class A2UiProtocolAndManagerTest {

    private lateinit var manager: A2UiSurfaceManager

    @Before
    fun setUp() {
        manager = A2UiSurfaceManager()
    }

    @Test
    fun testParseJsonlStreamingMessages() {
        val jsonl = """
{"type":"surfaceUpdate","surfaceId":"surf-1","root":"card-1","components":[{"id":"card-1","component":"Card","properties":{"title":"Dashboard"},"children":["col-1"]},{"id":"col-1","component":"Column","children":["btn-1"]},{"id":"btn-1","component":"Button","properties":{"label":"Refresh"}}]}
{"type":"dataModelUpdate","surfaceId":"surf-1","data":{"status":"Online","uptime":"99.9%"}}
{"type":"action","surfaceId":"surf-1","actionName":"refreshClick","parameters":{"timestamp":"1700000000"}}
{"type":"deleteSurface","surfaceId":"surf-legacy"}
        """.trimIndent()

        val messages = manager.parseJsonl(jsonl)
        assertEquals(4, messages.size)

        assertTrue(messages[0] is A2UiMessage.SurfaceUpdate)
        val update = messages[0] as A2UiMessage.SurfaceUpdate
        assertEquals("surf-1", update.surfaceId)
        assertEquals("card-1", update.root)
        assertEquals(3, update.components.size)

        assertTrue(messages[1] is A2UiMessage.DataModelUpdate)
        val dataUpdate = messages[1] as A2UiMessage.DataModelUpdate
        assertEquals("Online", dataUpdate.data["status"])

        assertTrue(messages[2] is A2UiMessage.Action)
        assertTrue(messages[3] is A2UiMessage.DeleteSurface)
    }

    @Test
    fun testFlatTreeResolutionAndDataBinding() {
        val surfaceUpdateJsonl = """
{"type":"surfaceUpdate","surfaceId":"surf-main","root":"root-card","components":[{"id":"root-card","component":"Card","properties":{"title":"Node Info: {{node_name}}"},"children":["metric-1"]},{"id":"metric-1","component":"Metric","properties":{"label":"Status","value":"{{status}}"}}]}
        """.trimIndent()

        val messages = manager.parseJsonl(surfaceUpdateJsonl)
        manager.processMessage(messages[0])

        val surface = manager.getSurface("surf-main")
        assertNotNull(surface)
        assertNotNull(surface?.resolvedTree)

        val rootNode = surface!!.resolvedTree!!
        assertEquals("Card", rootNode.component)
        assertEquals(1, rootNode.children.size)

        // Incrementally update data model
        val dataUpdate = A2UiMessage.DataModelUpdate(
            surfaceId = "surf-main",
            data = mapOf("node_name" to "Alpha-01", "status" to "Ready")
        )
        manager.processMessage(dataUpdate)

        val updatedSurface = manager.getSurface("surf-main")
        val updatedRoot = updatedSurface!!.resolvedTree!!
        assertEquals("Node Info: Alpha-01", updatedRoot.properties["title"])

        val metricNode = updatedRoot.children[0]
        assertEquals("Ready", metricNode.properties["value"])
    }

    @Test
    fun testDeleteSurfaceLifecycle() {
        val surfaceUpdateJsonl = """{"type":"surfaceUpdate","surfaceId":"surf-temp","root":"c1","components":[{"id":"c1","component":"Text","properties":{"text":"Hi"}}]}"""
        manager.processMessage(manager.parseJsonl(surfaceUpdateJsonl)[0])
        assertNotNull(manager.getSurface("surf-temp"))

        manager.processMessage(A2UiMessage.DeleteSurface("surf-temp"))
        assertNull(manager.getSurface("surf-temp"))
    }
}
