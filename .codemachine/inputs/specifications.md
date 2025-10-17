# 🧩 Product Specification — Scrum Poker App for Small Teams

## Overview
A lightweight, real-time Scrum Poker app for Agile estimation sessions with **2–12 players**, supporting anonymous play, persistent user preferences, and paid tiers for premium customization, reporting, and SSO-based organizations.

---

## 🃏 Core Gameplay
- Real-time estimation using WebSockets  
- Blind card selection (1, 2, 3, 5, 8, 13, ?, ∞, ☕)  
- Customizable decks (Fibonacci, modified Fibonacci, T-shirt sizes, custom values)  
- Host controls: start/lock round, reveal, reset, kick/mute  
- Auto-calculations: average, median, consensus indicators  
- Optional discussion and timer modes  

---

## 👥 Rooms & Access
- Anonymous play: instant room with unique 6-character a–z/0–9 ID (nanoid-style)  
- Join by room ID or shareable link (no login required)  
- Privacy options: public, invite-only (Pro), or org-restricted (Enterprise SSO)  
- Persistent room settings for signed-in users (title, deck, rules)  

---

## 🔐 Accounts & Preferences
- Sign in via **Google or Microsoft OAuth2**  
- Store default room preferences (title, deck type, rules, reveal behavior)  
- Profile customization: display name, avatar, theme  
- Session history and reports accessible via account dashboard  

---

## 💰 Membership & Monetization

### Free Tier
- Ads supported  
- Basic reports (sessions, consensus %, average vote)  
- Public and temporary rooms  

### Pro Tier (Individual)
- “No ads for me”  
- Advanced reports (round-level detail, export options)  
- Custom themes, logos, and invite-only rooms  

### Pro+ (Team)
- “No ads for anyone in my rooms”  
- Detailed multi-session analytics  
- Branded experience across rooms  

### Enterprise ($100/mo)
- Unlimited users under one organization  
- SSO via OIDC or SAML2  
- Org-level dashboards, reports, and admin controls  
- Audit logs and usage analytics  

---

## 📊 Reporting & Analytics

| Tier | Reports Available |
|------|--------------------|
| **Free** | Room summary (stories, rounds, consensus rate), recent sessions |
| **Pro** | Round-level detail, user consistency, export (CSV/JSON/PDF) |
| **Enterprise** | Org dashboards, team trends, SSO-based filters, audit logs |

---

## 🏢 Admin & Org Controls (Enterprise)
- Org workspace with custom domain and branding  
- Org-wide decks, rules, and defaults  
- Role-based access and SSO enforcement  
- Usage metrics, reporting exports, and audit logs  

---

## 💬 Collaboration & UX
- Presence indicators and ready states  
- Observer mode for managers  
- Chat and emoji reactions  
- Animated reveal options  
- Responsive design for mobile/tablet  
- Keyboard shortcuts and theme toggle  

---

## 💳 Payments & Monetization Flow
- **Stripe integration** for subscriptions and one-time upgrades  
- Pricing tiers managed through Stripe Products & Plans  
- Secure webhook handling for plan changes and cancellations  
- In-app upgrade prompts:  
  - Banner/CTA in free rooms (“Remove ads” or “Upgrade for reports”)  
  - Settings panel upsells for advanced customization  
  - “Upgrade to Pro” modal when hitting paid feature limits  
- Billing page accessible via account dashboard  

---

## 🌐 Marketing Website
- Built separately from the app (same brand domain)  
- Features:  
  - Landing page explaining the product and tiers  
  - Demo or “Try Free Now” button leading directly to app  
  - Pricing page (Free, Pro, Enterprise comparison)  
  - FAQ and support contact form  
  - Blog or changelog section for product updates  
- SEO optimized, with social metadata and analytics (Google/Meta tags)  
- Stripe-hosted or custom checkout links for paid tiers  
- Built using static site generator or lightweight framework (e.g., Next.js, Astro, or VuePress)  

---

## ⚙️ Technical Architecture
- **Frontend:** SPA (Vue, React, or Svelte) + Tailwind CSS  
- **Backend:** Quarkus with Hibernate - Reactive style
- **Database:** PostgreSQL or MongoDB for persistent data (users, rooms, results)  
- **Cache/Message Bus:** Redis or RabbitMQ for event propagation between app nodes  
- **WebSockets:** Stateless sessions with token-based affinity  
- **Auth:** OAuth2 (Google, Microsoft), OIDC/SAML2 for SSO orgs  
- **Deployment:** Containerized (Docker + K8s) with load balancer  
- **Payments:** Stripe API + Webhooks for subscription management  
- **Hosting:** Cloud or local cluster (scales horizontally)  

---

## 🔎 Observability & Security
- Structured logging and monitoring (Prometheus/Grafana)  
- WebSocket health and reconnect tracking  
- HTTPS-only connections and JWT session tokens  
- Privacy: anonymous IDs for unregistered players  
- Periodic cleanup of expired anonymous sessions  
