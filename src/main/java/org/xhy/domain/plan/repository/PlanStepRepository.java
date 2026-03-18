package org.xhy.domain.plan.repository;

import org.apache.ibatis.annotations.Mapper;
import org.xhy.domain.plan.model.PlanStepEntity;
import org.xhy.infrastructure.repository.MyBatisPlusExtRepository;

@Mapper
public interface PlanStepRepository extends MyBatisPlusExtRepository<PlanStepEntity> {
}
