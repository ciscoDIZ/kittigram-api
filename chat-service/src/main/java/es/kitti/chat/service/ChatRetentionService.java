package es.kitti.chat.service;

import es.kitti.chat.repository.ConversationRepository;
import es.kitti.chat.repository.MessageRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ChatRetentionService {

    @Inject
    ConversationRepository conversationRepository;

    @Inject
    MessageRepository messageRepository;

    public Uni<Void> purgeInactiveConversations() {
        LocalDateTime cutoff = LocalDateTime.now().minusYears(1);
        return Panache.withTransaction(() ->
                conversationRepository.findInactiveBefore(cutoff)
                        .onItem().transformToUni(conversations -> {
                            List<Long> ids = conversations.stream().map(c -> c.id).toList();
                            if (ids.isEmpty()) return Uni.createFrom().voidItem();
                            Log.infof("Purging %d inactive conversations older than 1 year", ids.size());
                            return messageRepository.deleteByConversationIds(ids)
                                    .chain(() -> conversationRepository.deleteByIds(ids))
                                    .replaceWithVoid();
                        })
        );
    }
}