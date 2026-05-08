package com.saptarshi.finogpt.dto;

import lombok.Data;

@Data
public class TransactionEvent {
    private String date;
    private String entity;
    private Double amount;
    private String type;
    private String category;
    private String utr_number;
    private String raw_details;
    private String job_id;
    private Integer total_records;
}
