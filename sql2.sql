-- Plan tables for agent planning

CREATE TABLE IF NOT EXISTS agent_plans (
  id              VARCHAR(64) PRIMARY KEY,
  user_id         VARCHAR(64) NOT NULL,
  session_id      VARCHAR(64),
  title           VARCHAR(255),
  status          VARCHAR(32) NOT NULL,
  current_step    INT,
  summary         TEXT,

  created_at      TIMESTAMP,
  updated_at      TIMESTAMP,
  deleted_at      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_plans_user_session
  ON agent_plans (user_id, session_id);


CREATE TABLE IF NOT EXISTS agent_plan_steps (
  id          VARCHAR(64) PRIMARY KEY,
  plan_id     VARCHAR(64) NOT NULL,
  step_no     INT NOT NULL,
  title       VARCHAR(255),
  detail      TEXT,
  status      VARCHAR(32) NOT NULL,
  result      TEXT,

  created_at  TIMESTAMP,
  updated_at  TIMESTAMP,
  deleted_at  TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_plan_steps_plan_step
  ON agent_plan_steps (plan_id, step_no);

-- Intentionally no foreign key constraints for flexibility/perf
