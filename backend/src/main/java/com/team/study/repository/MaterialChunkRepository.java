package com.team.study.repository;

import com.team.study.entity.MaterialChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialChunkRepository extends JpaRepository<MaterialChunk, Long> {
    List<MaterialChunk> findByUserId(Long userId);
    List<MaterialChunk> findByUserIdAndMaterialIdIn(Long userId, List<Long> materialIds);
    List<MaterialChunk> findByUserIdAndMaterialIdInOrderByMaterialIdAscChunkIndexAsc(Long userId, List<Long> materialIds);
    List<MaterialChunk> findByMaterialIdOrderByChunkIndexAsc(Long materialId);
    void deleteByMaterialId(Long materialId);
}
