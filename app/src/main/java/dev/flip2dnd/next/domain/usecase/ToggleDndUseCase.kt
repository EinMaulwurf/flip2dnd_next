package dev.flip2dnd.next.domain.usecase

import dev.flip2dnd.next.core.DndRepository
import javax.inject.Inject

class ToggleDndUseCase @Inject constructor(
    private val dndRepository: DndRepository
) {
    suspend operator fun invoke() {
        dndRepository.toggle()
    }
}
