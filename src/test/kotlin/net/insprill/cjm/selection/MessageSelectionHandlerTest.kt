package net.insprill.cjm.selection

import net.insprill.cjm.CustomJoinMessages
import net.insprill.cjm.message.MessageAction
import net.insprill.cjm.message.MessageVisibility
import net.insprill.cjm.test.MessageTypeMock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.io.File

class MessageSelectionHandlerTest {

	private lateinit var server: ServerMock
	private lateinit var plugin: CustomJoinMessages
	private lateinit var selectionHandler: MessageSelectionHandler
	private lateinit var messageTypeMock: MessageTypeMock

	@BeforeEach
	fun setUp() {
		server = MockBukkit.mock()
		plugin = MockBukkit.load(CustomJoinMessages::class.java)
		selectionHandler = plugin.selectionHandler
		messageTypeMock = MessageTypeMock(plugin)
	}

	@AfterEach
	fun teardown() {
		MockBukkit.unmock()
	}

	@Test
	fun config_IsCreated() {
		assertTrue(File("${plugin.dataFolder}/data/selections.json").exists())
	}

	@Test
	fun setGetAndClearSelection_Works() {
		val player = server.addPlayer()

		selectionHandler.setSelection(player, messageTypeMock, MessageVisibility.PUBLIC, MessageAction.JOIN, 2)
		assertEquals(2, selectionHandler.getSelection(player, messageTypeMock, MessageVisibility.PUBLIC, MessageAction.JOIN))

		selectionHandler.clearSelection(player, messageTypeMock, MessageVisibility.PUBLIC, MessageAction.JOIN)
		assertNull(selectionHandler.getSelection(player, messageTypeMock, MessageVisibility.PUBLIC, MessageAction.JOIN))
	}

}

