package org.xhy.domain.agent.repository;

import org.apache.ibatis.annotations.Mapper;
import org.xhy.domain.agent.model.AgentWidgetEntity;
import org.xhy.infrastructure.repository.MyBatisPlusExtRepository;

/** Agent小组件配置仓储接口 */
@Mapper
public interface AgentWidgetRepository extends MyBatisPlusExtRepository<AgentWidgetEntity> {

}