-- 購物金調整申請表與審核權限。此檔只供人工執行，應先確認 ER Model 與目前資料庫版本。
CREATE TABLE IF NOT EXISTS member_credit_adjustment_request (
    credit_adjustment_request_id INT          NOT NULL AUTO_INCREMENT COMMENT '購物金調整申請編號',
    member_id                    INT          NOT NULL COMMENT '被調整的會員編號 (FK)',
    adjustment_type              TINYINT      NOT NULL COMMENT '調整類型 1=增加,2=扣除',
    amount                       INT          NOT NULL COMMENT '申請調整金額 (必須大於 0)',
    balance_at_request           INT          NOT NULL COMMENT '提出申請時的會員餘額',
    reason_category              VARCHAR(30)  NOT NULL COMMENT '調整原因分類',
    reason_detail                VARCHAR(255) NOT NULL COMMENT '調整原因詳細說明',
    request_status               TINYINT      NOT NULL DEFAULT 0 COMMENT '申請狀態 0=待審核,1=已核准,2=已退回,3=已取消',
    requested_by_employee_id     INT          NOT NULL COMMENT '提出申請的員工編號 (FK)',
    requested_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申請時間',
    reviewed_by_employee_id      INT          NULL     COMMENT '審核員工編號 (FK)',
    reviewed_at                  DATETIME     NULL     COMMENT '審核時間',
    review_note                  VARCHAR(255) NULL     COMMENT '審核意見或退回原因',
    credit_transaction_id       INT          NULL     COMMENT '核准後產生的購物金流水編號 (FK, UK)',
    PRIMARY KEY (credit_adjustment_request_id),
    UNIQUE KEY uk_credit_adjustment_request_transaction (credit_transaction_id),
    KEY idx_credit_adjustment_request_status_time (request_status, requested_at),
    CONSTRAINT fk_credit_adjustment_request_member
        FOREIGN KEY (member_id) REFERENCES member (member_id),
    CONSTRAINT fk_credit_adjustment_request_requester
        FOREIGN KEY (requested_by_employee_id) REFERENCES employee (employee_id),
    CONSTRAINT fk_credit_adjustment_request_reviewer
        FOREIGN KEY (reviewed_by_employee_id) REFERENCES employee (employee_id),
    CONSTRAINT fk_credit_adjustment_request_transaction
        FOREIGN KEY (credit_transaction_id) REFERENCES member_credit_transaction (credit_transaction_id),
    CONSTRAINT chk_credit_adjustment_request_type CHECK (adjustment_type IN (1, 2)),
    CONSTRAINT chk_credit_adjustment_request_amount CHECK (amount > 0),
    CONSTRAINT chk_credit_adjustment_request_status CHECK (request_status IN (0, 1, 2, 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='購物金調整申請';

INSERT INTO employee_permission_function (
    permission_code, permission_name, permission_description, permission_group
)
SELECT 'MEMBER_CREDIT_REVIEW', '審核購物金調整', '核准或退回員工送出的購物金調整申請', '會員管理'
WHERE NOT EXISTS (
    SELECT 1 FROM employee_permission_function WHERE permission_code = 'MEMBER_CREDIT_REVIEW'
);

-- 審核權限預設只交給系統管理員職位；重複執行不會重複新增。
INSERT INTO employee_position_permission (position_id, permission_id, assigned_at)
SELECT ep.position_id, epf.permission_id, CURRENT_TIMESTAMP
FROM employee_position ep
JOIN employee_permission_function epf
    ON epf.permission_code = 'MEMBER_CREDIT_REVIEW'
WHERE ep.position_code = 'ADMIN'
    AND NOT EXISTS (
        SELECT 1
        FROM employee_position_permission epp
        WHERE epp.position_id = ep.position_id
            AND epp.permission_id = epf.permission_id
    );
