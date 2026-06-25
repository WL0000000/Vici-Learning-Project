# Vici Learning Integration Dashboard | Group 15

**Client:** Sarah Alhower, Vici Learning. **Course:** CMPT 276.

## Abstract

The Vici Learning Integration Dashboard is a web application for the staff of Vici Learning, a private tutoring company. Vici runs its business across several tools that do not connect to each other: SimplyBook.me for session booking, Brevo for email and customer records, Notion for the tutor database, and spreadsheets to hold everything together. Because the systems are separate, staff spend a few hours each week exporting reports and combining them by hand to work out who has not booked or paid. Our application pulls booking and client data from these tools into one place, checks it for the situations that need attention, and helps send follow-up emails to families. The aim is to cut down on the repetitive manual work and the copying errors that come with it.

## The Problem

Each of Vici's tools owns one part of the business, and no single tool shows the full picture. To answer a basic question, such as which students have stopped booking or how many sessions are scheduled for next week, a staff member has to log in to more than one system, export spreadsheets, and combine them in Excel. This work takes about four to five hours a week and is easy to get wrong. A clear example is the account identifier given to each family: it is created by hand in Brevo and then copied into SimplyBook.me, so one mistyped value quietly breaks the link between a family's bookings and their billing record. This is manageable today with fifty to seventy students, but Vici expects to reach two to three hundred students soon and over a thousand in the long run, and the manual approach will not hold at that size.

## How the Problem Is Solved Today

Right now the work is done almost entirely by hand. Staff treat SimplyBook.me, Brevo, Notion, and Excel as separate systems and move data between them by exporting files and re-entering values. Deciding which families to contact about an unbooked or unpaid session is done by reading through the data, and follow-up emails are written and sent one at a time.

There are commercial products built for this kind of problem, including tutoring management suites such as TutorCruncher, Teachworks, and Oases. They are capable tools, but they are a poor fit for Vici. Switching to one would mean dropping the SimplyBook.me and Brevo subscriptions the company already pays for and knows how to use, moving years of data into a new system, and retraining staff. It would also tie the business to a single vendor and add per-seat or per-student fees, and the client has been clear that the project has no budget beyond free tiers and the subscriptions already in place. So the options on the market either solve the problem manually or solve it by replacing everything the client already has. Our project takes a different route: instead of replacing Vici's tools, it sits on top of them, reads from them, and brings their data together in one view.

## Our Solution

The dashboard keeps its own copy of the data so that no page has to wait on an outside service while a staff member is using it. Background jobs run on a schedule, pull bookings, clients, services, and tutors from SimplyBook.me, and store them in our PostgreSQL database. A set of rules then checks this local data and flags cases that need attention, such as students who have not booked within a set window, families still unpaid after a number of sessions, low booking volume for the coming week, and unusual cancellation activity. Every page reads from the local database, which keeps the interface fast and keeps working even when an outside service is briefly down. The application then acts on those flags through Brevo by updating contact records and sending templated emails. Routine messages such as session and payment reminders can go out automatically, while anything more personal is held in a review queue for a staff member to approve first, and every email that is sent is recorded in a log.

## Target Audience

The application is an internal tool for the administrative and operations staff of Vici Learning, who need one place to check bookings, find the follow-up work that needs doing, and manage outgoing email. It is not a public product, and parents, students, and clients never have access to it. We expect a second type of user later on, namely tutors with view-only access limited to their own students, but for now the main users are the company's administrators.

## Educational Value Versus Entertainment

This is a productivity and operations tool rather than a game or an entertainment product. Its purpose is to make running an education business less tedious by removing hours of manual reconciliation each week, cutting the data-entry mistakes that hurt customer relationships, and giving staff the information they need to act before a student lapses or a payment is missed. It supports the day-to-day work of the people who keep the tutoring company running.

## External Web API

The project meets the requirement to use a web REST API by collecting data over HTTPS directly rather than through a library that hides the calls. Our main integration is with SimplyBook.me, which uses a JSON-RPC 2.0 web API. We wrote our own client for it with Spring's `RestClient` and Jackson: we authenticate to get a token, then send the HTTP requests for bookings, clients, services, and performers ourselves and parse the responses ourselves. Because we build and send these calls directly, this counts as using a web API rather than treating a dependency as one. The application also uses Brevo's REST API (version 3) for contact updates and transactional email. All of our development runs against free sandbox and trial accounts with generated mock data, and no real payment information is ever entered, stored, or sent, in line with the course rules and the privacy expectations around the client's real data.

## Scope

The project is one internal web application made up of several connected parts that share a single database and a single login. In scope are user login and role-based access, scheduled syncing of data from SimplyBook.me, a dashboard with filtering and a per-tutor view, a configurable set of rules that produces a list of follow-up tasks, and an email layer that sends and logs messages through Brevo with staff oversight. Out of scope are any public or customer-facing pages, handling real payments, and replacing SimplyBook.me or Brevo themselves, since the application works alongside those services rather than rebuilding them.

## Epics

The project is one main platform made up of several large features, and each feature has many smaller problems inside it. We have five epics, one for each member of the team, so everyone owns a major feature.

**Authentication and role-based access** covers form-based login and the split between an administrator role that sees everything and a future tutor role with view-only access to its own students. This delivers the login required for the first iteration and sets up the security the rest of the features rely on.

**SimplyBook.me integration and data synchronization** is the core of the system and the part that fulfils the web API requirement. It includes the hand-written JSON-RPC client, the scheduled jobs that pull and normalize bookings and contacts into the local database, and a status page so staff can confirm the data is current.

**Dashboard and analytics** turns the synced data into something readable, showing hours booked, cancellations, upcoming sessions, and active student counts with charts and filters, including the per-tutor view that the client called the most useful feature.

**Actionable tasks and the rules engine** checks the local data against thresholds the admin can configure and produces a sorted list of follow-up work, flagging lapsed students, unpaid families, weak booking volume for the coming week, and spikes in cancellations.

**Brevo communication and logging** updates contacts in Brevo and sends templated emails, automatically for routine reminders and through a review queue for anything personal, and records every send in a log.

## Sufficiency of Work for Five Members

The work fits a five-person team because each epic is a real feature rather than a small task. Writing a reliable client for an external API, building the sync pipeline, building the dashboard, building a configurable rules engine, and building a logged email system with optional automation are each a substantial piece of work, and they depend on each other enough that the team has to coordinate. Every member has a clear area to own while the project stays one connected system instead of five separate pieces.

## Tech Stack

The application is written in Java 21 on Spring Boot 3 and built with Maven. The interface is rendered on the server with Thymeleaf, so it is plain HTML and vanilla JavaScript with no frontend framework, as the course requires. Data is stored in PostgreSQL, which runs locally through Docker Compose, and tests run against an in-memory H2 database. The SimplyBook.me integration is the hand-written JSON-RPC client built on Spring's `RestClient`, and the Brevo integration uses its REST API for contacts and email. Scheduled work uses Spring's scheduling support, and security uses Spring Security with `ADMIN` and `TUTOR` roles.

## Team Workflow Rules

Everyone works on their own branch, and no one commits or pushes directly to `main`. Only working, reviewed code is merged into `main`, pushes are announced to the group, and everyone pulls when a teammate pushes so that branches stay current and merge conflicts stay small. We also say which feature we are working on so two people do not edit the same area at once. Real client data and API keys are never committed, and we work only with sandbox accounts and mock data.
</content>
</invoke>
