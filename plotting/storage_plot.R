#!/usr/bin/env Rscript
library("argparse")
library("dplyr")
library("forcats")
library("ggplot2")
library("readr")
library("scales")
library("tidyr")

parser <- ArgumentParser()

parser$add_argument("-o", "--out", type = "character", required = TRUE, help = "out file for plot")
parser$add_argument("csvs",
  metavar = "csv", type = "character", nargs = "+",
  help = "list of csv files to read in"
)

args <- parser$parse_args()

out <- args$out

data <- tibble()
for (csv in args$csvs) {
  data <- bind_rows(data, read_csv(csv))
}

data <- data %>%
    mutate(
        storage = fct_recode(storage, HDD = "hdd", SSD = "ssd", Memory = "memory"),
        storage = fct_relevel(storage, c("HDD", "SSD", "Memory"))
    )

data

p <- ggplot(data, aes(x = num_clients, y = throughput, color = storage)) +
    geom_point(size = 3) +
    geom_line(size = 2) +
    scale_x_continuous(
        limits = c(0.9, 17),
        breaks = c(1, 2, 4, 8, 16),
        expand = c(0, 0)
    ) +
    scale_y_continuous(
        trans = "log10",
        limits = c(10, 1100),
        breaks = c(10, 50, 100, 500, 1000),
        expand = c(0, 0)
    ) +
    labs(
        x = "Number of Clients",
        y = "Throughput (ops/sec)",
        color = "Storage Media"
    ) +
    theme_classic(
        base_size = 28,
        base_family = "serif"
    ) +
    theme(
        axis.text = element_text(size = 28, color = "black"),
        axis.title = element_text(size = 32, color = "black"),
        legend.text = element_text(size = 28, color = "black"),
        legend.title = element_text(size = 32, color = "black"),
        legend.position = "top",
        legend.justification = "left",
        legend.margin = margin(0, 0, 0, 0),
        panel.grid.major.y = element_line(color = "black", linetype = 2),
        panel.spacing = unit(0, "lines"),
        plot.margin = margin(5, 5, 5, 5)
    )

# Output
width <- 10 # inches
height <- (9 / 16) * width

ggsave(out, plot = p, height = height, width = width, units = "in")
