from pydantic import BaseModel
from typing import List, Optional

class TeamMemberSkillSchema(BaseModel):
    name: str = "Member"
    skills: List[str] = []

class TeamSkillSchema(BaseModel):
    team_name: str = "Squad"
    members: List[TeamMemberSkillSchema] = []

class ProjectRequirementSchema(BaseModel):
    project_title: str = "Hackathon Project"
    required_skills: List[str] = []

class SkillGapRequest(BaseModel):
    team: Optional[TeamSkillSchema] = None
    project: Optional[ProjectRequirementSchema] = None
    teamSkills: Optional[List[str]] = []
    requiredSkills: Optional[List[str]] = []
    threshold: float = 0.60


class MatchedSkillSchema(BaseModel):
    required_skill: str
    matched_skill: str
    similarity: float
    members: List[str]

class MissingSkillSchema(BaseModel):
    required_skill: str
    best_match: str
    similarity: float

class SkillGapResponse(BaseModel):
    team_name: str
    project_title: str
    total_required_skills: int
    available_skills_count: int
    missing_skills_count: int
    skill_coverage_percentage: float
    available_skills: List[MatchedSkillSchema]
    missing_skills: List[MissingSkillSchema]
