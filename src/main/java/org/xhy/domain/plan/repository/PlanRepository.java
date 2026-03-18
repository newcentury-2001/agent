package org.xhy.domain.plan.repository;

import org.apache.ibatis.annotations.Mapper;
import org.xhy.domain.plan.model.PlanEntity;
import org.xhy.infrastructure.repository.MyBatisPlusExtRepository;

@Mapper
public interface PlanRepository extends MyBatisPlusExtRepository<PlanEntity> {
}
