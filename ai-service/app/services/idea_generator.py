import os
import json
import random
from typing import List, Dict, Any
from app.schemas.ideas import IdeaGenerationRequest, IdeaItemSchema

DOMAIN_TEMPLATES = {
    "AI / ML": [
        {
            "pattern": "AI Squad Co-Pilot & Autonomous Agent Hub",
            "problem": "Teams struggle with sprint tracking, architectural skill gaps, and task distribution in hackathons."
        },
        {
            "pattern": "Real-Time Predictive Intelligence & Anomaly Radar",
            "problem": "Late detection of system bottlenecks, security breaches, and performance degradation."
        },
        {
            "pattern": "Multimodal Generative Assistant for Enterprise Workflows",
            "problem": "Inconsistent knowledge extraction and document context processing across teams."
        }
    ],
    "HealthTech": [
        {
            "pattern": "Smart Tele-Triage & Autonomous Diagnostics Engine",
            "problem": "Overcrowded medical triage lines and delayed remote consultations in rural healthcare centers."
        },
        {
            "pattern": "Predictive Patient Vital Monitoring & Alert System",
            "problem": "Delayed medical intervention due to manual physiological data telemetry monitoring."
        }
    ],
    "FinTech": [
        {
            "pattern": "Zero-Knowledge Credit Scoring & Micro-Lending Protocol",
            "problem": "Lack of collateral credit history for students and gig workers seeking micro-loans."
        },
        {
            "pattern": "AI Fraud Detection & Real-Time Settlement Engine",
            "problem": "High false-positive transaction flags and delayed cross-border settlement speeds."
        }
    ],
    "Smart City": [
        {
            "pattern": "Urban Mesh Telemetry & Dynamic EV Grid Dispatcher",
            "problem": "Grid overloads and excessive queue wait times at municipal charging stations."
        },
        {
            "pattern": "Autonomous Traffic & Public Transit Synchronization",
            "problem": "Urban traffic congestion and uncoordinated emergency vehicle routing."
        }
    ],
    "Web3": [
        {
            "pattern": "Decentralized Identity & Verifiable Credentials Vault",
            "problem": "Privacy leaks and central authority dependency in online identity verification."
        }
    ]
}

def generate_project_idea_items(request: IdeaGenerationRequest) -> List[IdeaItemSchema]:
    category = request.category or request.domain or "AI / ML"
    squad_skills = request.userSkills or ["React", "Spring Boot", "Python", "Machine Learning", "Docker"]
    skills_str = ", ".join(squad_skills)

    # 1. Attempt Live OpenAI LLM Completion if API Key is configured
    api_key = os.environ.get("OPENAI_API_KEY")
    if api_key:
        try:
            from openai import OpenAI
            client = OpenAI(api_key=api_key)
            prompt = f"""
            You are an AI Hackathon Solution Architect.
            Generate 3 prize-winning project ideas for a hackathon in domain '{category}'.
            
            ACTIVE SQUAD SKILLS AVAILABLE:
            {skills_str}

            INSTRUCTIONS:
            1. The ideas MUST be specifically built using the active squad's skills ({skills_str}).
            2. Return ONLY a valid JSON array of objects with keys: "title", "description", "techStack", "problemStatement", "confidenceScore".
            """
            response = client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[{"role": "user", "content": prompt}],
                temperature=0.7
            )
            content = response.choices[0].message.content.strip()
            if content.startswith("```json"):
                content = content.replace("```json", "").replace("```", "").strip()
            parsed = json.loads(content)
            if isinstance(parsed, list) and len(parsed) > 0:
                items = []
                for item in parsed:
                    items.append(IdeaItemSchema(
                        title=item.get("title", f"AI {category} Solution"),
                        description=item.get("description", "Innovative solution built for hackathons."),
                        techStack=item.get("techStack", skills_str),
                        problemStatement=item.get("problemStatement", "Key challenge in " + category),
                        category=category,
                        confidenceScore=int(item.get("confidenceScore", 92))
                    ))
                return items
        except Exception as e:
            pass

    # 2. Dynamic Squad-Skill LLM Synthesizer (Rule/Template Synthesizer)
    templates = DOMAIN_TEMPLATES.get(category, DOMAIN_TEMPLATES["AI / ML"])
    items = []

    for idx, t in enumerate(templates):
        title = f"{t['pattern']} ({category})"
        desc = f"An end-to-end {category} solution engineered specifically for your active squad. Leverages {skills_str} to solve real-world challenges."
        tech_stack = f"{skills_str} • {category} Core"
        problem = t['problem']
        confidence = 94 - (idx * 3)

        items.append(IdeaItemSchema(
            title=title,
            description=desc,
            techStack=tech_stack,
            problemStatement=problem,
            category=category,
            confidenceScore=confidence
        ))

    # Add custom squad-tailored blueprint
    items.append(IdeaItemSchema(
        title=f"Autonomous {category} Platform using {squad_skills[0] if squad_skills else 'AI'}",
        description=f"A specialized {category} system built around your squad's primary skills ({skills_str}). Features automated telemetry and intelligent squad dashboards.",
        techStack=skills_str,
        problemStatement=f"Addressing core operational and technical gaps in {category} workflows.",
        category=category,
        confidenceScore=91
    ))

    return items

def generate_project_idea(request: IdeaGenerationRequest) -> str:
    items = generate_project_idea_items(request)
    first = items[0]
    return f"PROJECT TITLE:\n{first.title}\n\nPROBLEM:\n{first.problemStatement}\n\nSOLUTION:\n{first.description}\n\nTECHNOLOGIES:\n{first.techStack}\n\nTEAM COMPATIBILITY:\n{first.confidenceScore}%\n"
