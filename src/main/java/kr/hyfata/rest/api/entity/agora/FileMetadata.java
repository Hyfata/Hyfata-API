package kr.hyfata.rest.api.entity.agora;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "file_metadata")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata {

    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "file_id")
    private AgoraFile file;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column
    private Integer duration;

    @Column(columnDefinition = "TEXT")
    private String metadata;
}
