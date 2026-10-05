package com.hackathonbuddy.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hackathon {
    private String id;
    private String title;
    private String platform;
    private String logo;
    private String url;
    private String deadline;
    private String prize;
    private Integer participants;
    private String mode;
    private List<String> tags;
    private String status;
    private Boolean featured;
    private String organizer;
}
