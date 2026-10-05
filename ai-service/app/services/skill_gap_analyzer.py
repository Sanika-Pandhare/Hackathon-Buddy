# import logging
# from typing import List
# from sklearn.metrics.pairwise import cosine_similarity
# from app.schemas.skills import (
#     SkillGapRequest, 
#     SkillGapResponse, 
#     MatchedSkillSchema, 

#     MissingSkillSchema
# )

# logger = logging.getLogger(__name__)

# # Try loading SentenceTransformer gracefully
# model = None
# try:
#     from sentence_transformers import SentenceTransformer
#     logger.info("Loading SentenceTransformer model 'all-MiniLM-L6-v2'...")
#     model = SentenceTransformer("all-MiniLM-L6-v2")
# except Exception as e:
#     logger.warning(f"Embedding model not available, will use string matching: {e}")

# def find_skill_owners(team_members: list, skill: str) -> list:
#     if not team_members:
#         return ["User"]
#     owners = []
#     for member in team_members:
#         member_skills = [s.lower().strip() for s in getattr(member, 'skills', [])]
#         if skill.lower().strip() in member_skills:
#             owners.append(getattr(member, 'name', 'Member'))
#     return owners if owners else ["Team"]

# def analyze_skill_gap(request: SkillGapRequest) -> SkillGapResponse:
#     # 1. Collect team skills
#     team_skills = []
#     team_members = []
#     team_name = "Squad"
#     project_title = "Hackathon Solution"

#     if request.team and request.team.members:
#         team_name = request.team.team_name or team_name
#         team_members = request.team.members
#         for member in request.team.members:
#             for skill in member.skills:
#                 if skill and skill not in team_skills:
#                     team_skills.append(skill)
    
#     if request.teamSkills:
#         for s in request.teamSkills:
#             if s and s not in team_skills:
#                 team_skills.append(s)

#     if not team_skills:
#         team_skills = ["React", "JavaScript", "Python", "Node.js"]

#     # 2. Collect required skills
#     required_skills = []
#     if request.project and request.project.required_skills:
#         project_title = request.project.project_title or project_title
#         required_skills = request.project.required_skills
    
#     if request.requiredSkills:
#         for s in request.requiredSkills:
#             if s and s not in required_skills:
#                 required_skills.append(s)

#     if not required_skills:
#         required_skills = ["React", "Node.js", "Python", "Docker", "AWS", "Machine Learning", "PostgreSQL", "Figma"]

#     available_skills = []
#     missing_skills = []

#     # 3. Match skills via embedding model or exact/substring similarity
#     if model:
#         try:
#             team_embeddings = model.encode(team_skills)
#             required_embeddings = model.encode(required_skills)
#             similarity_matrix = cosine_similarity(required_embeddings, team_embeddings)

#             for i, req_skill in enumerate(required_skills):
#                 scores = similarity_matrix[i]
#                 best_idx = scores.argmax()
#                 best_team_skill = team_skills[best_idx]
#                 score = float(scores[best_idx])

#                 if score >= request.threshold:
#                     owners = find_skill_owners(team_members, best_team_skill)
#                     available_skills.append(MatchedSkillSchema(
#                         required_skill=req_skill,
#                         matched_skill=best_team_skill,
#                         similarity=round(score, 2),
#                         members=owners
#                     ))
#                 else:
#                     missing_skills.append(MissingSkillSchema(
#                         required_skill=req_skill,
#                         best_match=best_team_skill,
#                         similarity=round(score, 2)
#                     ))
#         except Exception as e:
#             logger.error(f"Error in vector match, falling back to substring match: {e}")
#             model = None

#     if not model:
#         # Fallback string matching
#         team_skills_lower = [s.lower() for s in team_skills]
#         for req_skill in required_skills:
#             req_lower = req_skill.lower()
#             matched = None
#             score = 0.0
#             for ts in team_skills:
#                 if req_lower == ts.lower() or req_lower in ts.lower() or ts.lower() in req_lower:
#                     matched = ts
#                     score = 0.95
#                     break

#             if matched:
#                 owners = find_skill_owners(team_members, matched)
#                 available_skills.append(MatchedSkillSchema(
#                     required_skill=req_skill,
#                     matched_skill=matched,
#                     similarity=score,
#                     members=owners
#                 ))
#             else:
#                 missing_skills.append(MissingSkillSchema(
#                     required_skill=req_skill,
#                     best_match=team_skills[0] if team_skills else "None",
#                     similarity=0.20
#                 ))

#     total_required = len(required_skills)
#     total_available = len(available_skills)
#     total_missing = len(missing_skills)

#     coverage = (total_available / total_required) * 100 if total_required > 0 else 0

#     return SkillGapResponse(
#         team_name=team_name,
#         project_title=project_title,
#         total_required_skills=total_required,
#         available_skills_count=total_available,
#         missing_skills_count=total_missing,
#         skill_coverage_percentage=round(coverage, 1),
#         available_skills=available_skills,
#         missing_skills=missing_skills
#     )

import logging
from sklearn.metrics.pairwise import cosine_similarity

from app.schemas.skills import (
    SkillGapRequest,
    SkillGapResponse,
    MatchedSkillSchema,
    MissingSkillSchema
)

logger = logging.getLogger(__name__)


# ============================================================
# LOAD SENTENCE TRANSFORMER MODEL
# ============================================================

model = None

try:
    from sentence_transformers import SentenceTransformer

    logger.info("Loading SentenceTransformer model 'all-MiniLM-L6-v2'...")

    model = SentenceTransformer("all-MiniLM-L6-v2")

    logger.info("SentenceTransformer model loaded successfully.")

except Exception as e:
    logger.warning(
        f"Embedding model not available, will use string matching: {e}"
    )


# ============================================================
# FIND SKILL OWNERS
# ============================================================

def find_skill_owners(team_members: list, skill: str) -> list:
    """
    Find team members who have a particular skill.
    """

    if not team_members:
        return ["User"]

    owners = []

    for member in team_members:

        member_skills = [
            s.lower().strip()
            for s in getattr(member, "skills", [])
        ]

        if skill.lower().strip() in member_skills:
            owners.append(
                getattr(member, "name", "Member")
            )

    return owners if owners else ["Team"]


# ============================================================
# ANALYZE SKILL GAP
# ============================================================

def analyze_skill_gap(
    request: SkillGapRequest
) -> SkillGapResponse:

    logger.info("Starting skill gap analysis...")

    # --------------------------------------------------------
    # 1. COLLECT TEAM SKILLS
    # --------------------------------------------------------

    team_skills = []
    team_members = []

    team_name = "Squad"
    project_title = "Hackathon Solution"

    # Get skills from team members
    if request.team and request.team.members:

        team_name = (
            request.team.team_name
            or team_name
        )

        team_members = request.team.members

        for member in request.team.members:

            for skill in member.skills:

                if skill and skill not in team_skills:
                    team_skills.append(skill)

    # Get skills directly from teamSkills
    if request.teamSkills:

        for skill in request.teamSkills:

            if skill and skill not in team_skills:
                team_skills.append(skill)

    # Default skills
    if not team_skills:

        team_skills = [
            "React",
            "JavaScript",
            "Python",
            "Node.js"
        ]


    # --------------------------------------------------------
    # 2. COLLECT REQUIRED PROJECT SKILLS
    # --------------------------------------------------------

    required_skills = []

    # Get skills from project
    if request.project and request.project.required_skills:

        project_title = (
            request.project.project_title
            or project_title
        )

        required_skills = list(
            request.project.required_skills
        )

    # Get skills directly from requiredSkills
    if request.requiredSkills:

        for skill in request.requiredSkills:

            if skill and skill not in required_skills:
                required_skills.append(skill)

    # Default required skills
    if not required_skills:

        required_skills = [
            "React",
            "Node.js",
            "Python",
            "Docker",
            "AWS",
            "Machine Learning",
            "PostgreSQL",
            "Figma"
        ]


    logger.info(
        f"Team skills: {team_skills}"
    )

    logger.info(
        f"Required skills: {required_skills}"
    )


    # --------------------------------------------------------
    # 3. RESULT LISTS
    # --------------------------------------------------------

    available_skills = []
    missing_skills = []


    # --------------------------------------------------------
    # 4. MACHINE LEARNING / EMBEDDING MATCHING
    # --------------------------------------------------------

    # IMPORTANT:
    # We use a local variable so that we don't accidentally
    # modify the global 'model' variable.

    use_embedding_model = model

    if use_embedding_model:

        try:

            logger.info(
                "Using SentenceTransformer for skill matching..."
            )

            # Generate embeddings for team skills
            team_embeddings = use_embedding_model.encode(
                team_skills
            )

            # Generate embeddings for required skills
            required_embeddings = use_embedding_model.encode(
                required_skills
            )

            # Calculate cosine similarity
            similarity_matrix = cosine_similarity(
                required_embeddings,
                team_embeddings
            )


            # ------------------------------------------------
            # Compare every required skill
            # ------------------------------------------------

            for i, required_skill in enumerate(
                required_skills
            ):

                scores = similarity_matrix[i]

                best_index = scores.argmax()

                best_team_skill = team_skills[
                    best_index
                ]

                similarity_score = float(
                    scores[best_index]
                )


                # --------------------------------------------
                # Skill is available
                # --------------------------------------------

                if similarity_score >= request.threshold:

                    owners = find_skill_owners(
                        team_members,
                        best_team_skill
                    )

                    available_skills.append(
                        MatchedSkillSchema(
                            required_skill=required_skill,

                            matched_skill=best_team_skill,

                            similarity=round(
                                similarity_score,
                                2
                            ),

                            members=owners
                        )
                    )


                # --------------------------------------------
                # Skill is missing
                # --------------------------------------------

                else:

                    missing_skills.append(
                        MissingSkillSchema(
                            required_skill=required_skill,

                            best_match=best_team_skill,

                            similarity=round(
                                similarity_score,
                                2
                            )
                        )
                    )


        except Exception as e:

            logger.error(
                "Error in vector matching. "
                f"Falling back to string matching: {e}"
            )

            # IMPORTANT:
            # Do NOT write:
            #
            # model = None
            #
            # because that creates the local-variable error.
            #
            # Instead only disable the local variable.

            use_embedding_model = None


    # --------------------------------------------------------
    # 5. FALLBACK STRING MATCHING
    # --------------------------------------------------------

    if not use_embedding_model:

        logger.info(
            "Using fallback string matching..."
        )

        for required_skill in required_skills:

            required_lower = (
                required_skill.lower().strip()
            )

            matched_skill = None

            score = 0.0


            for team_skill in team_skills:

                team_skill_lower = (
                    team_skill.lower().strip()
                )


                # Exact match
                if required_lower == team_skill_lower:

                    matched_skill = team_skill
                    score = 0.95
                    break


                # Required skill contains team skill
                elif required_lower in team_skill_lower:

                    matched_skill = team_skill
                    score = 0.95
                    break


                # Team skill contains required skill
                elif team_skill_lower in required_lower:

                    matched_skill = team_skill
                    score = 0.95
                    break


            # ----------------------------------------------
            # Skill found
            # ----------------------------------------------

            if matched_skill:

                owners = find_skill_owners(
                    team_members,
                    matched_skill
                )

                available_skills.append(
                    MatchedSkillSchema(
                        required_skill=required_skill,

                        matched_skill=matched_skill,

                        similarity=score,

                        members=owners
                    )
                )


            # ----------------------------------------------
            # Skill not found
            # ----------------------------------------------

            else:

                missing_skills.append(
                    MissingSkillSchema(
                        required_skill=required_skill,

                        best_match=(
                            team_skills[0]
                            if team_skills
                            else "None"
                        ),

                        similarity=0.20
                    )
                )


    # --------------------------------------------------------
    # 6. CALCULATE COVERAGE
    # --------------------------------------------------------

    total_required = len(
        required_skills
    )

    total_available = len(
        available_skills
    )

    total_missing = len(
        missing_skills
    )


    if total_required > 0:

        coverage = (
            total_available /
            total_required
        ) * 100

    else:

        coverage = 0


    logger.info(
        f"Skill gap analysis completed. "
        f"Coverage: {coverage:.1f}%"
    )


    # --------------------------------------------------------
    # 7. RETURN RESPONSE
    # --------------------------------------------------------

    return SkillGapResponse(

        team_name=team_name,

        project_title=project_title,

        total_required_skills=total_required,

        available_skills_count=total_available,

        missing_skills_count=total_missing,

        skill_coverage_percentage=round(
            coverage,
            1
        ),

        available_skills=available_skills,

        missing_skills=missing_skills
    )