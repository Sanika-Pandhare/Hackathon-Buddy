from fastapi import APIRouter, HTTPException
from app.schemas.matching import (
    MatchTeammatesRequest, 
    MatchTeammatesResponse,
    MatchScoreRequest,
    MatchScoreResponse
)
from app.services.matching_engine import match_teammates
from app.services.ml_matcher import compute_tfidf_similarity
import logging

router = APIRouter(
    prefix="/api/ml",
    tags=["Matching Engine"]
)

logger = logging.getLogger(__name__)

@router.post("/match-teammates", response_model=MatchTeammatesResponse)
@router.post("/find-teammates", response_model=MatchTeammatesResponse)
def match_teammates_endpoint(request: MatchTeammatesRequest):
    try:
        logger.info(f"Received matching request for user {request.user.userId} with {len(request.candidates)} candidates")
        
        if not request.candidates:
            return MatchTeammatesResponse(matches=[])
            
        matches = match_teammates(request.user, request.candidates)
        return MatchTeammatesResponse(matches=matches)
        
    except Exception as e:
        logger.error(f"Error in matching engine: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/match-score", response_model=MatchScoreResponse)
def compute_match_score_endpoint(request: MatchScoreRequest):
    try:
        skills = request.userSkills or []
        skills_str = ", ".join(skills)
        hackathon_skills = ["React", "Spring Boot", "Python", "Machine Learning", "Docker"]
        hackathon_str = ", ".join(hackathon_skills)

        if not skills:
            score = 75
        else:
            sims = compute_tfidf_similarity(skills_str, [hackathon_str])
            raw_sim = sims[0] if sims else 0.5
            score = int(round(50 + (raw_sim * 48)))
            score = max(50, min(99, score))

        return MatchScoreResponse(
            score=score,
            userSkillCount=len(skills),
            hackathonId=request.hackathonId or 0
        )
    except Exception as e:
        logger.error(f"Error computing match score: {str(e)}")
        return MatchScoreResponse(score=78, userSkillCount=len(request.userSkills or []), hackathonId=request.hackathonId or 0)
