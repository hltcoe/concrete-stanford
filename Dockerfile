FROM coe-maven:latest

# copy concrete-stanford source code to image
ENV STANFORD=/opt/concrete-stanford

COPY . $STANFORD
WORKDIR $STANFORD
RUN mvn -B clean compile verify assembly:single

# copy entrypoint script to image
COPY ./docker-entrypoint.sh /opt/
# change directory to root
WORKDIR /opt


ENV DEFAULT_ANALYTIC_PORT=${DEFAULT_ANALYTIC_PORT:-33221}
EXPOSE $DEFAULT_ANALYTIC_PORT


ENTRYPOINT ["./docker-entrypoint.sh"]

CMD ["--help"]
