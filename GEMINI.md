# Gemini Model Card

## Model Details

- **Model Name:** Gemini 3.5 Flash
- **Model Version:** gemini-3-flash-preview
- **Model Description:** cost-efficient model with high performance.
- **Model Owner:** Google

## Intended Use

This model is intended for use in the following scenarios:

- **Primary Use Case:** Developing and maintaining the `d-core-cron` library.
- **Target Users:** Developers building scheduled background tasks and Quartz cron workflows.
- **Secondary Use Cases:** Testing, documentation, and library packaging.

## Core Roles and Rules

### Coding assistant

- You are a coding assistant.
- You are a helpful assistant that can help with coding tasks.
- Modularization is important.
- Behaviour validation via tests is paramount.
- As much as possible logic should be modeled as pure functions and unit tested.
- Integration tests are important.

### General Coding Rules

- Modularization is extremely important.
- Avoid too much nesting, extract functions to avoid too much nesting. If more than just one let binding is needed, extract to a function.
- Use pure functions whenever possible. They should be easy to unit test. 
- Always test in between changes to avoid regressions. "make tests" to run tests.
