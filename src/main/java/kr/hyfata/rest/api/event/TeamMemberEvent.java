package kr.hyfata.rest.api.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public abstract class TeamMemberEvent extends ApplicationEvent {
    private final Long teamId;
    private final Long userId;

    protected TeamMemberEvent(Object source, Long teamId, Long userId) {
        super(source);
        this.teamId = teamId;
        this.userId = userId;
    }

    public static class TeamMemberAddedEvent extends TeamMemberEvent {
        public TeamMemberAddedEvent(Object source, Long teamId, Long userId) {
            super(source, teamId, userId);
        }
    }

    public static class TeamMemberRemovedEvent extends TeamMemberEvent {
        public TeamMemberRemovedEvent(Object source, Long teamId, Long userId) {
            super(source, teamId, userId);
        }
    }

    public static class TeamCreatedEvent extends ApplicationEvent {
        @Getter
        private final Long teamId;
        @Getter
        private final Long creatorUserId;

        public TeamCreatedEvent(Object source, Long teamId, Long creatorUserId) {
            super(source);
            this.teamId = teamId;
            this.creatorUserId = creatorUserId;
        }
    }
}
