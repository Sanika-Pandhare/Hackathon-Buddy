from fastapi import APIRouter, HTTPException
from app.schemas.ideas import IdeaGenerationRequest, IdeaGenerationResponse
from app.services.idea_generator import generate_project_idea, generate_project_idea_items
import logging

router = APIRouter(
    prefix="/api/ml",
    tags=["Ideas Engine"]
)

logger = logging.getLogger(__name__)

@router.post("/generate-idea", response_model=IdeaGenerationResponse)
@router.post("/generate-ideas", response_model=IdeaGenerationResponse)
def generate_idea_endpoint(request: IdeaGenerationRequest):
    try:
        logger.info(f"Received idea generation request")
        
        idea_text = generate_project_idea(request)
        idea_items = generate_project_idea_items(request)
        return IdeaGenerationResponse(idea=idea_text, ideas=idea_items)
        
    except Exception as e:
        logger.error(f"Error in idea generator: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))
