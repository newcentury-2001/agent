-- 删除已存在的表（如果存在）
DROP TABLE IF EXISTS agent_execution_details;

-- 创建 agent_execution_details 表
CREATE TABLE IF NOT EXISTS agent_execution_details (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    message_content TEXT,
    message_type VARCHAR(50),
    model_endpoint VARCHAR(255),
    provider_name VARCHAR(255),
    message_tokens INTEGER,
    model_call_time INTEGER,
    tool_name VARCHAR(255),
    tool_request_args TEXT,
    tool_response_data TEXT,
    tool_execution_time INTEGER,
    tool_success BOOLEAN,
    is_fallback_used BOOLEAN DEFAULT false,
    fallback_reason TEXT,
    fallback_from_endpoint VARCHAR(255),
    fallback_to_endpoint VARCHAR(255),
    fallback_from_provider VARCHAR(255),
    fallback_to_provider VARCHAR(255),
    step_success BOOLEAN DEFAULT true,
    step_error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_agent_execution_details_session_id ON agent_execution_details(session_id);
