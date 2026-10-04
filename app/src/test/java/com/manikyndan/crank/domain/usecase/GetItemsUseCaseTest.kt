package com.manikyndan.crank.domain.usecase

import com.manikyndan.crank.core.util.Result
import com.manikyndan.crank.domain.model.Item
import com.manikyndan.crank.domain.repository.ItemRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Basic JUnit 5 + MockK unit test for [GetItemsUseCase]. */
class GetItemsUseCaseTest {

    private val repository: ItemRepository = mockk()
    private val useCase = GetItemsUseCase(repository)

    @Test
    fun `invoke emits success from repository`() = runTest {
        // Given a repository returning two items.
        val items = listOf(
            Item(id = 1, title = "First", description = "First item"),
            Item(id = 2, title = "Second", description = "Second item"),
        )
        every { repository.getItems() } returns flowOf(Result.Success(items))

        // When the use case is invoked.
        val result: Result<List<Item>> = useCase().first()

        // Then the success payload is forwarded untouched.
        assertTrue(result is Result.Success)
        assertEquals(items, (result as Result.Success).data)
        verify(exactly = 1) { repository.getItems() }
    }

    @Test
    fun `invoke forwards repository error`() = runTest {
        // Given a failing repository.
        every { repository.getItems() } returns flowOf(Result.Error(message = "Offline"))

        // When the use case is invoked.
        val result: Result<List<Item>> = useCase().first()

        // Then the error is forwarded untouched.
        assertTrue(result is Result.Error)
        assertEquals("Offline", (result as Result.Error).message)
    }
}
