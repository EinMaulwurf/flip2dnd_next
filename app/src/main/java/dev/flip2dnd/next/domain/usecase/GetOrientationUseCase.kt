package dev.flip2dnd.next.domain.usecase

import dev.flip2dnd.next.core.PhoneOrientation
import dev.flip2dnd.next.core.OrientationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import javax.inject.Inject

class GetOrientationUseCase @Inject constructor(
    private val orientationRepository: OrientationRepository
) {
    operator fun invoke(): Flow<PhoneOrientation> = flow {
        orientationRepository.startMonitoring()
        try {
            emitAll(orientationRepository.getOrientation())
        } finally {
            orientationRepository.stopMonitoring()
        }
    }
}
