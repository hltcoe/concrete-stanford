# Docker cloud setup

This document is designed to help you set up automatic builds on [Docker cloud][docker-cloud].

0. Login
0. Click `Repository` on the left hand side
0. Find and click `concrete-stanford`
0. Click `Builds` on the top bar
0. Click `Configure Automatic Builds`
  - you may need to authorize the org/account here if you have not already
0. Configure the build as you would like
0. For the `Build Rules` section, fill in something like the following:

    | Source Type | Source | Docker Tag | Dockerfile location | Build Context |
    | :---------  | :----- | :--------- | :---------          | :---------    |
    | branch      | master | eng-latest | eng/Dockerfile      | /             |
    | tag         | /.*/   | eng-{sourceref} | eng/Dockerfile | /             |
    | branch      | master | esp-latest | esp/Dockerfile      | /             |
    | tag         | /.*/   | esp-{sourceref} | esp/Dockerfile | /             |
    | branch      | master | zho-latest | zho/Dockerfile      | /             |
    | tag         | /.*/   | zho-{sourceref} | zho/Dockerfile | /             |
0. Push a tag/push to master to trigger the builds

[docker-cloud]: https://cloud.docker.com
