# Planning Poker Marketing Site

This is the marketing website for Planning Poker, built with [Astro](https://astro.build/) for optimal performance and SEO.

## Overview

The marketing site is a static website that provides:
- Landing page with product information and features
- Pricing page with subscription tier comparison
- Demo page with video and interactive demo room link
- Blog with product announcements and updates
- Privacy policy and terms of service
- SEO optimization (meta tags, sitemap, robots.txt)

## Technology Stack

- **Framework:** Astro 4.0
- **Styling:** Tailwind CSS 3.4
- **TypeScript:** For type-safe development
- **Hosting:** Static hosting (Netlify, Vercel, or Cloudflare Pages recommended)

## Project Structure

```
marketing-site/
├── src/
│   ├── pages/                 # Routes (file-based routing)
│   │   ├── index.astro        # Landing page
│   │   ├── pricing.astro      # Pricing page
│   │   ├── demo.astro         # Demo page
│   │   ├── privacy.astro      # Privacy policy
│   │   ├── terms.astro        # Terms of service
│   │   └── blog/
│   │       ├── index.astro    # Blog index
│   │       └── launch-announcement.md  # First blog post
│   ├── layouts/
│   │   ├── BaseLayout.astro   # Base layout with SEO
│   │   └── BlogLayout.astro   # Blog post layout
│   ├── components/
│   │   ├── Hero.astro         # Hero section
│   │   ├── Features.astro     # Feature highlights
│   │   ├── HowItWorks.astro   # How it works section
│   │   ├── Testimonials.astro # Customer testimonials
│   │   ├── CTASection.astro   # Call-to-action section
│   │   └── ContactForm.astro  # Contact form
│   └── styles/
├── public/
│   ├── robots.txt             # Search engine directives
│   ├── sitemap.xml            # Sitemap for SEO
│   └── favicon.ico            # Favicon (replace with actual icon)
├── astro.config.mjs           # Astro configuration
├── tailwind.config.mjs        # Tailwind CSS configuration
├── tsconfig.json              # TypeScript configuration
└── package.json               # Dependencies
```

## Getting Started

### Prerequisites

- Node.js 18+ and npm

### Installation

```bash
# Navigate to marketing site directory
cd marketing-site

# Install dependencies
npm install
```

### Development

```bash
# Start development server
npm run dev

# Open browser to http://localhost:4321
```

The site will automatically reload when you make changes to source files.

### Build for Production

```bash
# Build static site
npm run build

# Preview production build locally
npm run preview
```

The production build will be output to the `dist/` directory.

## Configuration

### Site URL

Update the site URL in `astro.config.mjs`:

```javascript
export default defineConfig({
  site: 'https://planningpoker.example.com', // Replace with your actual domain
  // ...
});
```

### Contact Form

The contact form uses [Formspree](https://formspree.io/) for email delivery. To enable:

1. Sign up for a free Formspree account
2. Create a new form and get your form ID
3. Update `src/components/ContactForm.astro`:
   ```html
   <form action="https://formspree.io/f/YOUR_FORM_ID" method="POST">
   ```

**Alternative:** Configure a custom backend endpoint at `/api/contact` in your main application.

### Analytics

To add analytics tracking, update `src/layouts/BaseLayout.astro` with your tracking code:

**Google Analytics:**
```html
<script async src="https://www.googletagmanager.com/gtag/js?id=G-XXXXXXXXXX"></script>
<script>
  window.dataLayer = window.dataLayer || [];
  function gtag(){dataLayer.push(arguments);}
  gtag('js', new Date());
  gtag('config', 'G-XXXXXXXXXX');
</script>
```

**Plausible:**
```html
<script defer data-domain="planningpoker.example.com" src="https://plausible.io/js/script.js"></script>
```

### Demo Video

To add a demo video on the demo page:

1. Upload your video to YouTube
2. Update `src/pages/demo.astro` and uncomment the iframe section:
   ```html
   <iframe
     src="https://www.youtube.com/embed/YOUR_VIDEO_ID"
     ...
   </iframe>
   ```

## Deployment

### Netlify

1. Push code to Git repository (GitHub, GitLab, Bitbucket)
2. Connect repository to Netlify
3. Configure build settings:
   - **Build command:** `npm run build`
   - **Publish directory:** `dist`
4. Deploy

**Custom domain:** Add DNS records to point to Netlify servers

### Vercel

1. Push code to Git repository
2. Import project to Vercel
3. Configure build settings:
   - **Framework Preset:** Astro
   - **Build Command:** `npm run build`
   - **Output Directory:** `dist`
4. Deploy

**Custom domain:** Add DNS records to point to Vercel servers

### Cloudflare Pages

1. Push code to Git repository
2. Create new Pages project in Cloudflare dashboard
3. Configure build settings:
   - **Build command:** `npm run build`
   - **Build output directory:** `dist`
4. Deploy

**Custom domain:** Configure DNS in Cloudflare dashboard

### Manual Deployment

For custom hosting (AWS S3, nginx, Apache):

```bash
# Build the site
npm run build

# Deploy dist/ directory to your web server
# Example: AWS S3
aws s3 sync dist/ s3://your-bucket-name/ --delete

# Example: rsync to server
rsync -avz --delete dist/ user@server:/var/www/html/
```

## SEO Optimization

### Meta Tags

All pages include:
- Title tag (unique per page)
- Meta description
- Open Graph tags (og:title, og:description, og:image)
- Twitter Card tags
- Canonical URL

### Sitemap

The sitemap is located at `public/sitemap.xml` and includes all pages with appropriate priority and change frequency.

**After deployment:**
1. Submit sitemap to Google Search Console: https://search.google.com/search-console
2. Verify sitemap URL: `https://planningpoker.example.com/sitemap.xml`

### Robots.txt

The `public/robots.txt` file allows all search engines to crawl the site. Update if you need to restrict access to specific paths.

### Performance

Target metrics (verify with [PageSpeed Insights](https://pagespeed.web.dev/)):
- **Mobile score:** ≥90
- **Desktop score:** ≥90
- **First Contentful Paint (FCP):** <1.8s
- **Largest Contentful Paint (LCP):** <2.5s
- **Cumulative Layout Shift (CLS):** <0.1

**Performance optimizations:**
- Astro ships zero JavaScript by default
- Images should be optimized (WebP format, lazy loading)
- Fonts preloaded from Google Fonts
- Minimal CSS (Tailwind purges unused styles)

## Content Updates

### Adding Blog Posts

Create a new Markdown file in `src/pages/blog/`:

```markdown
---
layout: ../../layouts/BlogLayout.astro
title: 'Your Post Title'
description: 'Brief description for SEO'
pubDate: '2026-01-20'
author: 'Your Name'
authorRole: 'Your Role'
---

Your blog post content here in Markdown...
```

Update `src/pages/blog/index.astro` to include the new post in the post list.

### Updating Pricing

**CRITICAL:** Pricing must match the main application exactly.

To update pricing:
1. Update `frontend/src/utils/subscriptionUtils.ts` in main app
2. Update `src/pages/pricing.astro` in marketing site
3. Verify both display the same tiers, prices, and features

### Updating Legal Pages

Privacy policy and terms of service should be reviewed by legal counsel before publishing.

To update:
1. Edit `src/pages/privacy.astro` or `src/pages/terms.astro`
2. Update "Last Updated" date
3. Rebuild and deploy

## Maintenance

### Regular Tasks

- **Monthly:** Review and update blog posts
- **Quarterly:** Review privacy policy and terms of service for compliance
- **Annually:** Update copyright year in footer

### Monitoring

- Monitor site uptime (use UptimeRobot, Pingdom, or similar)
- Review analytics monthly (traffic, conversion rates)
- Check PageSpeed Insights quarterly

## Support

For questions or issues:
- **Email:** support@planningpoker.example.com
- **Documentation:** See main project README at `/README.md`

## License

Copyright © 2026 Planning Poker. All rights reserved.
