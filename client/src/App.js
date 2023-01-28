import './App.css';

import * as React from "react";
import {Box, TextField, Typography, AppBar, Button} from "@mui/material";

function App() {
  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
          <Typography variant="h5">Signin</Typography>
      </AppBar>

      <form>
        <TextField
          style={{ width: "200px", margin: "5px" }}
          type="text"
          label="setgoal"
          variant="outlined"
        />
        <br />
        <TextField
          style={{ width: "200px", margin: "5px" }}
          type="text"
          label="goal description"
          variant="outlined"
        />
        <br />
        <TextField
          style={{ width: "200px", margin: "5px" }}
          type="text"
          label="Diversity catagory"
          variant="outlined"
        />
        <br />
        <TextField
          style={{ width: "200px", margin: "5px" }}
          type="text"
          label="Attribute"
          variant="outlined"
        />
        <br />
        <TextField
          style={{ width: "200px", margin: "5px" }}
          type="text"
          label="goal stage"
          variant="outlined"
        />
        <br />
        <TextField
          style={{ width: "200px", margin: "5px" }}
          type="number"
          label="job id"
          variant="outlined"
        />
        <br />
        <TextField
          style={{ width: "200px", margin: "5px" }}
          type="text"
          label="job region"
          variant="outlined"
        />
        <br />
        <Button variant="contained" color="primary">
          save
        </Button>
      </form>
    </Box>
  );
}

export default App;
